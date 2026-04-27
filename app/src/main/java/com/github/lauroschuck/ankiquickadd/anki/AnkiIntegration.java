package com.github.lauroschuck.ankiquickadd.anki;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.ui.settings.SettingsActivity;
import com.google.android.material.snackbar.Snackbar;
import com.ichi2.anki.api.AddContentApi;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Handles the integration with AnkiDroid for creating language flashcards.
 */
public class AnkiIntegration {

    public static final int AD_PERM_REQUEST = 0;

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
        if (ankiDroidHelper.shouldShowRationale(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(R.string.permission_rationale_message)
                    .setPositiveButton(R.string.permission_grant_button, (dialog, which) -> {
                        ankiDroidHelper.requestPermission(activity, requestCode);
                    })
                    .setNegativeButton(R.string.permission_cancel_button, null)
                    .show();
        } else {
            // Check if we have already asked and been denied (permanent denial)
            // Note: This is a bit tricky to detect perfectly without tracking,
            // but usually if rationale is false and we don't have permission,
            // it means "Don't ask again" is active.
            if (!hasPermission()) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.permission_rationale_title)
                        .setMessage(R.string.permission_denied_permanent_message)
                        .setPositiveButton(R.string.permission_settings_button, (dialog, which) -> {
                            openAppSettings(activity);
                        })
                        .setNegativeButton(R.string.permission_cancel_button, null)
                        .show();
            } else {
                ankiDroidHelper.requestPermission(activity, requestCode);
            }
        }
    }

    public void openAppSettings(Activity activity) {
        var intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        var uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
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
            showSnackbar(context, "No cards selected.", true);
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
                var deckName = String.format(
                        "%s::%s-%s",
                        parentDeckName,
                        learningLanguage.getDisplayName(learningLanguage),
                        nativeLanguage.getDisplayName(learningLanguage));

                Timber.d("Target deck: %s", deckName);
                var deckId = getDeckId(deckName);
                var modelId = getModelId(deckId, note);

                var actualFieldNames = ankiDroidHelper.getApi().getFieldList(modelId);
                if (actualFieldNames == null) {
                    Timber.e("Could not get field list for modelId: %d", modelId);
                    showSnackbar(context, "Card add failed: Model Error", true);
                    return;
                }

                var audio = audioUrl == null ? null : processAudio(audioUrl);

                var fieldsList = note.generateFields(
                        learningLanguage, nativeLanguage, audio, sourceUrl, actualFieldNames, cards);

                var tagsList = new LinkedList<Set<String>>();
                for (int i = 0; i < fieldsList.size(); i++) {
                    tagsList.add(note.getTags());
                }

                ankiDroidHelper.removeDuplicates(fieldsList, tagsList, modelId);

                if (fieldsList.isEmpty()) {
                    Timber.i("No new notes to add (all were duplicates)");
                    showSnackbar(context, "Notes already exist in Anki", false);
                    return;
                }

                int added = ankiDroidHelper.getApi().addNotes(modelId, deckId, fieldsList, tagsList);
                Timber.i("Successfully added %d/%d notes to Anki", added, fieldsList.size());

                if (added > 0) {
                    showSnackbar(context, "Successfully sent " + added + " cards to Anki", false);
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
                    showSnackbar(context, "Card add failed: No notes added", true);
                }
            } catch (Exception e) {
                Timber.e(e, "Error adding cards to Anki");
                showSnackbar(context, "Error adding cards: " + e.getMessage(), true);
            }
        });
    }

    private String processAudio(Uri audioUrl) {
        var ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        if (audioUrl == null || ankiPkg == null) {
            return null;
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
            } finally {
                // Revoke permission after synchronous call is finished
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (IOException | RuntimeException e) {
            Timber.e(e, "Audio processing failed for URL %s", audioUrl);
        }
        return null;
    }

    private File downloadFile(Uri urlString, String fileName) throws IOException {
        var client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        var request = new Request.Builder()
                .url(urlString.toString())
                .header("User-Agent", "AnkiQuickAdd/1.0 (https://github.com/lauroschuck/ankiquickadd)")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download file: " + response);
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

    private static void showSnackbar(Activity activity, String message, boolean isError) {
        activity.runOnUiThread(() -> {
            var rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                var snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
                var bgColor = isError ? R.color.error_red : R.color.anki_blue;
                snackbar.setBackgroundTint(ContextCompat.getColor(activity, bgColor));
                snackbar.setTextColor(ContextCompat.getColor(activity, R.color.white));
                snackbar.show();
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
