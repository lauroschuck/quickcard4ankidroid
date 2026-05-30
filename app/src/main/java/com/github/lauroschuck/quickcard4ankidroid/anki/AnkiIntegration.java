package com.github.lauroschuck.quickcard4ankidroid.anki;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import com.github.lauroschuck.quickcard4ankidroid.AppConfig;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.DictionaryNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.TextNote;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainActivity;
import com.github.lauroschuck.quickcard4ankidroid.ui.settings.SettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ichi2.anki.api.AddContentApi;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Handles the integration with AnkiDroid for creating language flashcards.
 */
public class AnkiIntegration {

    public static final int AD_PERM_REQUEST = 0;
    private static final String KEY_ANKI_PERM_REQUESTED = "anki_perm_requested";

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AnkiDroidHelper ankiDroidHelper;
    private final Activity context;

    public AnkiIntegration(@NonNull Activity context) {
        this.context = context;
        this.ankiDroidHelper = new AnkiDroidHelper(context);
    }

    public boolean hasPermission() {
        return !ankiDroidHelper.shouldRequestPermission();
    }

    public void requestPermissionWithRationale(Activity activity, int requestCode) {
        if (!AnkiDroidHelper.isApiAvailable(activity)) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.error_anki_not_installed_title)
                    .setMessage(R.string.error_anki_not_installed_message)
                    .setPositiveButton(R.string.install_button, (dialog, which) -> openPlayStore(activity))
                    .setNegativeButton(R.string.permission_cancel_button, null)
                    .show();
        } else if (ankiDroidHelper.shouldShowRationale(activity)) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(R.string.permission_rationale_message)
                    .setPositiveButton(R.string.permission_proceed_button, (dialog, which) -> {
                        requestPermission(activity, requestCode);
                    })
                    .setNegativeButton(R.string.permission_cancel_button, null)
                    .show();
        } else {
            // Check if we have already asked and been denied (permanent denial)
            var prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean wasAsked = prefs.getBoolean(KEY_ANKI_PERM_REQUESTED, false);

            if (!hasPermission() && wasAsked) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.permission_rationale_title)
                        .setMessage(R.string.permission_denied_permanent_message)
                        .setPositiveButton(R.string.permission_settings_button, (dialog, which) -> {
                            openAppSettings(activity);
                        })
                        .setNegativeButton(R.string.permission_cancel_button, null)
                        .show();
            } else {
                requestPermission(activity, requestCode);
            }
        }
    }

    private void requestPermission(Activity activity, int requestCode) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.edit().putBoolean(KEY_ANKI_PERM_REQUESTED, true).apply();
        ankiDroidHelper.requestPermission(activity, requestCode);
    }

    public void openAppSettings(Activity activity) {
        var intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        var uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    private void openPlayStore(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki")));
        } catch (android.content.ActivityNotFoundException anfe) {
            activity.startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.ichi2.anki")));
        }
    }

    public <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> void addCards(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            Uri audioUrl,
            Uri sourceUrl,
            @NonNull N note,
            @NonNull List<I> cards) {
        addCards(learningLanguage, nativeLanguage, audioUrl, sourceUrl, note, cards, null);
    }

    public <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> void addCards(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            Uri audioUrl,
            Uri sourceUrl,
            @NonNull N note,
            @NonNull List<I> cards,
            Consumer<String> onSuccess) {
        if (cards.isEmpty()) {
            Timber.w("No cards selected to add");
            showSnackbar(context.getString(R.string.anki_no_cards_selected), true);
            return;
        }

        Timber.i(
                "Adding %d %s cards for %s-%s",
                cards.size(), note.getClass().getSimpleName(), learningLanguage, nativeLanguage);

        executorService.execute(() -> {
            try {
                var prefs = PreferenceManager.getDefaultSharedPreferences(context);
                var parentDeckName = prefs.getString(
                        SettingsActivity.KEY_PARENT_DECK_NAME, SettingsActivity.DEFAULT_PARENT_DECK_NAME);
                var subDeckName = prefs.getString(SettingsActivity.KEY_DECK_NAME, null);
                if (subDeckName == null || subDeckName.isEmpty()) {
                    throw new RuntimeException("Deck name missing");
                }

                var deckName = String.format("%s::%s", parentDeckName, subDeckName);

                Timber.d("Target deck: %s", deckName);
                var deckId = getDeckId(deckName);
                var modelId = getModelId(deckId, note);

                var actualFieldNames = ankiDroidHelper.getApi().getFieldList(modelId);
                if (actualFieldNames == null) {
                    throw new AnkiException(context.getString(R.string.anki_add_failed_model));
                }

                var audio = processAudio(audioUrl, cards.get(0).headword());
                if (audio == null) {
                    return;
                }

                var fieldsList = note.generateFields(
                        learningLanguage, nativeLanguage, audio, sourceUrl, actualFieldNames, cards);

                var tagsList = new LinkedList<Set<String>>();
                for (int i = 0; i < fieldsList.size(); i++) {
                    tagsList.add(note.getTags());
                }

                if (note instanceof DictionaryNote dn) {
                    // Conflicting dictionary notes might have different fields,
                    // but the selected definitions/examples probably don't match,
                    // so remove them do not allow it to go forward
                    var duplicates = ankiDroidHelper.findDuplicateIndexes(fieldsList, tagsList, modelId);
                    if (!duplicates.isEmpty()) {
                        var firstDuplicate = cards.get(duplicates.get(0));
                        var id = fieldsList.get(duplicates.get(0))[0];
                        Timber.w("Found duplicate note '%s', aborting", id);
                        showSnackbar(
                                context.getString(
                                        R.string.anki_duplicate_note,
                                        firstDuplicate.headword(),
                                        firstDuplicate.lexicalCategory()),
                                true);
                        return;
                    }
                } else if (note instanceof TextNote tn) {
                    // Example cards should be the same if it's a duplicate,
                    // so just remove the duplicates
                    ankiDroidHelper.removeDuplicates(fieldsList, tagsList, modelId);
                } else {
                    throw new AssertionError(
                            "Unexpected note type: " + note.getClass().getSimpleName());
                }

                if (fieldsList.isEmpty()) {
                    Timber.i("No new notes to add (all were duplicates)");
                    showSnackbar(context.getString(R.string.anki_notes_exist), false);
                    return;
                }

                int added = ankiDroidHelper.getApi().addNotes(modelId, deckId, fieldsList, tagsList);
                Timber.i("Successfully added %d/%d notes to Anki", added, fieldsList.size());

                if (added > 0) {
                    showSnackbar(
                            context.getResources().getQuantityString(R.plurals.anki_add_success, added, added), false);
                    if (onSuccess != null) {
                        for (int i = 0; i < added; i++) {
                            // This is a bit of a simplification as we don't know exactly which ones were added if some
                            // failed
                            // but usually it's all or nothing if they aren't duplicates
                            onSuccess.accept(cards.get(i).headword());
                        }
                    }
                } else {
                    Timber.w("No notes were added to Anki");
                    showSnackbar(context.getString(R.string.anki_add_failed_none), true);
                }
            } catch (RuntimeException e) {
                Timber.e(e, "Error adding cards to Anki");
                showSnackbar(context.getString(R.string.anki_add_failed_error, e.getMessage()), true);
            }
        });
    }

    /**
     * @return the media String if successful, empty if no audio or failed download where
     * the user accepted the lack of audio, or null if the user declined the lack of audio.
     */
    private String processAudio(Uri audioUrl, String headword) {
        if (audioUrl == null) {
            return "";
        }
        try {
            return addAudioMedia(audioUrl);
        } catch (AudioProcessingException e) {
            Timber.e(e, "Audio processing failed for %s", audioUrl);
            var latch = new CountDownLatch(1);
            var shouldProceed = new AtomicBoolean(false);

            var downloadException = findCause(e, DownloadException.class);
            var message = downloadException != null
                    ? context.getString(
                            R.string.anki_audio_failed_message_http, headword, downloadException.getHttpCode())
                    : context.getString(R.string.anki_audio_failed_message, headword);

            context.runOnUiThread(() -> {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.anki_audio_failed_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.anki_audio_failed_proceed, (dialog, which) -> {
                            shouldProceed.set(true);
                            latch.countDown();
                        })
                        .setNegativeButton(R.string.anki_audio_failed_cancel, (dialog, which) -> {
                            shouldProceed.set(false);
                            latch.countDown();
                        })
                        .setCancelable(false)
                        .show();
            });

            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }

            return shouldProceed.get() ? "" : null;
        }
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> clazz) {
        var current = throwable;
        while (current != null) {
            if (clazz.isInstance(current)) {
                return clazz.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private String addAudioMedia(Uri audioUrl) {
        var ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        if (ankiPkg == null) {
            throw new AudioProcessingException("AnkiDroid package not found");
        }

        Timber.d("Processing audio: %s", audioUrl);
        try {
            var fileName = UUID.randomUUID() + "." + MimeTypeMap.getFileExtensionFromUrl(audioUrl.toString());
            var localFile = downloadFile(audioUrl, fileName);
            var uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", localFile);

            context.grantUriPermission(ankiPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                String internalName = ankiDroidHelper.getApi().addMediaFromUri(uri, fileName, "audio");
                Timber.d("Media added to Anki with internal name: %s", internalName);
                if (internalName != null) {
                    return internalName;
                }
                throw new AudioProcessingException("AnkiDroid failed to add media");
            } finally {
                // Revoke permission after synchronous call is finished
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (IOException e) {
            throw new AudioProcessingException("Audio I/O failed", e);
        } catch (RuntimeException e) {
            throw new AudioProcessingException("Audio processing failed", e);
        }
    }

    private File downloadFile(Uri urlString, String fileName) throws IOException {
        var client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        var request = new Request.Builder()
                .url(urlString.toString())
                .header("User-Agent", AppConfig.USER_AGENT)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(new DownloadException(response.code()));
            }

            var file = new File(context.getCacheDir(), fileName);
            try (InputStream input = response.body().byteStream();
                    FileOutputStream output = new FileOutputStream(file)) {
                var buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            Timber.d("File downloaded to: %s", file.getAbsolutePath());
            return file;
        }
    }

    private long getDeckId(String deckName) {
        var deckId = ankiDroidHelper.findDeckIdByName(deckName);
        if (deckId == null) {
            Timber.i("Deck '%s' not found, creating new one", deckName);
            deckId = ankiDroidHelper.getApi().addNewDeck(deckName);
            if (deckId == null) {
                throw new IllegalStateException(String.format("Could not add deck '%s'", deckName));
            }
            ankiDroidHelper.storeDeckReference(deckName, deckId);
        }
        return deckId;
    }

    private <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> long getModelId(long deckId, N note) {
        var modelId = ankiDroidHelper.findModelIdByName(
                note.getModelName(), note.getFieldNames().size());
        if (modelId == null) {
            Timber.i("Model '%s' not found, creating new one", note.getModelName());
            modelId = ankiDroidHelper
                    .getApi()
                    .addNewCustomModel(
                            note.getModelName(),
                            note.getFieldNames().toArray(new String[] {}),
                            note.getCardNames(),
                            note.getQuestionTemplates(),
                            note.getAnswerTemplates(),
                            note.getCss(),
                            deckId,
                            null);
            if (modelId == null) {
                throw new IllegalStateException(String.format("Could not add model '%s'", note.getModelName()));
            }
            ankiDroidHelper.storeModelReference(note.getModelName(), modelId);
        }
        return modelId;
    }

    private void showSnackbar(String message, boolean isError) {
        if (context instanceof MainActivity mainActivity) {
            mainActivity.showSnackbar(message, isError);
        } else {
            context.runOnUiThread(() -> {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static class AudioProcessingException extends RuntimeException {
        public AudioProcessingException(String message) {
            super(message);
        }

        public AudioProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @RequiredArgsConstructor
    @Getter
    private static class DownloadException extends RuntimeException {
        private final int httpCode;
    }
}
