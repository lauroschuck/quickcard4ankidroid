package com.github.lauroschuck.ankiquickadd.anki;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
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

/**
 * Handles the integration with AnkiDroid for creating language flashcards.
 */
public class AnkiIntegration {

    private static final String TAG = "AnkiIntegration";
    private static final int AD_PERM_REQUEST = 0;
    private static final String DECK_NAME_TEMPLATE = "Anki Quick Add::%s-%s";

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AnkiDroidHelper ankiDroidHelper;
    private final Activity context;

    public AnkiIntegration(@NonNull Activity context) {
        this.context = context;
        this.ankiDroidHelper = new AnkiDroidHelper(context);
        if (ankiDroidHelper.shouldRequestPermission()) {
            ankiDroidHelper.requestPermission(context, AD_PERM_REQUEST);
        }
    }

    public <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> void addCards(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            String audioUrl,
            String sourceUrl,
            @NonNull N note,
            @NonNull List<I> cards) {
        addCards(learningLanguage, nativeLanguage, audioUrl, sourceUrl, note, cards, null);
    }

    public <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> void addCards(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            String audioUrl,
            String sourceUrl,
            @NonNull N note,
            @NonNull List<I> cards,
            Consumer<String> onSuccess) {
        if (cards.isEmpty()) {
            showSnackbar(context, "No cards selected.", true);
            return;
        }
        Log.i(
                TAG,
                String.format(
                        "Adding %s-%s %s cards: %s",
                        learningLanguage, nativeLanguage, note.getClass().getSimpleName(), cards));

        executorService.execute(() -> {
            var deckName = String.format(
                    DECK_NAME_TEMPLATE, learningLanguage.getDisplayName(), nativeLanguage.getDisplayName());
            var deckId = getDeckId(deckName);
            var modelId = getModelId(deckId, note);

            var actualFieldNames = ankiDroidHelper.getApi().getFieldList(modelId);
            if (actualFieldNames == null) {
                showSnackbar(context, "Card add failed: Model Error", true);
                return;
            }

            var audio = audioUrl == null ? null : processAudio(audioUrl);

            var fieldsList =
                    note.generateFields(learningLanguage, nativeLanguage, audio, sourceUrl, actualFieldNames, cards);

            var tagsList = new LinkedList<Set<String>>();
            for (int i = 0; i < fieldsList.size(); i++) {
                tagsList.add(note.getTags());
            }

            ankiDroidHelper.removeDuplicates(fieldsList, tagsList, modelId);
            int added = ankiDroidHelper.getApi().addNotes(modelId, deckId, fieldsList, tagsList);

            if (added > 0) {
                showSnackbar(context, "Successfully sent " + added + " cards to Anki", false);
                if (onSuccess != null) {
                    for (I card : cards) {
                        onSuccess.accept(card.headword());
                    }
                }
            } else {
                showSnackbar(context, "Card add failed: No notes added", true);
            }
        });
    }

    private String processAudio(String audioUrl) {
        var ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        if (audioUrl == null || audioUrl.isEmpty() || ankiPkg == null) {
            // TODO some handling for this
            return null;
        }
        if (audioUrl.startsWith("//")) {
            audioUrl = "https:" + audioUrl;
        }

        try {
            var fileName = UUID.randomUUID() + "." + MimeTypeMap.getFileExtensionFromUrl(audioUrl);
            var localFile = downloadFile(audioUrl, fileName);
            var uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", localFile);

            context.grantUriPermission(ankiPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                String internalName = ankiDroidHelper.getApi().addMediaFromUri(uri, fileName, "audio");
                if (internalName != null) {
                    return internalName;
                }
            } finally {
                // Revoke permission after synchronous call is finished
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio processing failed for URL " + audioUrl, e);
        }
        // TODO some handling for this
        return null;
    }

    private File downloadFile(String urlString, String fileName) throws IOException {
        var client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        var request = new Request.Builder()
                .url(urlString)
                .header("User-Agent", "SwedishAnkiQuickAdd/1.0 (https://github.com/lauroschuck/ankiquickadd)")
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
            return file;
        }
    }

    private long getDeckId(String deckName) {
        var deckId = ankiDroidHelper.findDeckIdByName(deckName);
        if (deckId == null) {
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
