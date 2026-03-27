package com.github.lauroschuck.ankiquickadd.anki;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.github.lauroschuck.ankiquickadd.MainActivity;
import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.google.android.material.snackbar.Snackbar;
import com.ichi2.anki.api.AddContentApi;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Handles the integration with AnkiDroid for creating language flashcards.
 */
public class AnkiIntegration<N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> {

    private static final String TAG = "AnkiIntegration";
    private static final int AD_PERM_REQUEST = 0;
    private static final String DECK_NAME = "Anki Quick Add::Swedish-English";

    private final AnkiDroidHelper mAnkiDroid;
    private final MainActivity context;
    //    private final DictionaryNote dictionaryNote;
    private final N note;
    //    private final TextNote textNote;

    public AnkiIntegration(@NonNull MainActivity context, @NonNull N note) {
        this.context = context;
        this.mAnkiDroid = new AnkiDroidHelper(context);
        //        this.dictionaryNote = dictionaryNote;
        //        this.textNote = textNote;
        this.note = note;
        if (mAnkiDroid.shouldRequestPermission()) {
            mAnkiDroid.requestPermission(context, AD_PERM_REQUEST);
        }
    }

    public static <N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> void createAnkiCards(
            MainActivity context, N note, List<I> cards) {
        if (cards == null || cards.isEmpty()) {
            showSnackbar(context, "No cards selected.", true);
            return;
        }
        new Thread(() -> new AnkiIntegration(context, note).addCardsToAnkiDroid(cards)).start();
    }

    private void addCardsToAnkiDroid(final List<I> data) {
        Long deckId = getDeckId();
        Long modelId = getModelId(note);

        if (deckId == null || modelId == null) {
            showSnackbar(context, "Card add failed: API Error", true);
            return;
        }

        String[] actualFieldNames = mAnkiDroid.getApi().getFieldList(modelId);
        if (actualFieldNames == null) {
            showSnackbar(context, "Card add failed: Model Error", true);
            return;
        }

        var audioCache = new HashMap<String, String>();

        List<String[]> fieldsList = note.generateFields(
                actualFieldNames,
                data,
                (card, audioUrl) -> audioCache.computeIfAbsent(audioUrl, headword -> processAudio(audioUrl)));

        LinkedList<Set<String>> tagsList = new LinkedList<>();
        for (int i = 0; i < fieldsList.size(); i++) {
            tagsList.add(note.getTags());
        }

        mAnkiDroid.removeDuplicates(fieldsList, tagsList, modelId);
        int added = mAnkiDroid.getApi().addNotes(modelId, deckId, fieldsList, tagsList);

        if (added != 0) {
            showSnackbar(context, "Successfully sent " + added + " cards to Anki", false);
        } else {
            showSnackbar(context, "Card add failed: No notes added", true);
        }
    }

    private void sanitizeFields(String[] fields) {
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) {
                fields[i] = "";
            }
        }
    }

    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        // Allow only <b> and <br> tags. Strips links and other tags.
        return Jsoup.clean(html, Safelist.none().addTags("b", "br"));
    }

    private String processAudio(String audioUrl) {
        var ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        if (audioUrl == null || audioUrl.isEmpty() || ankiPkg == null) {
            // TODO some handling for this
            return null;
        }
        if (ankiPkg.startsWith("//")) {
            ankiPkg = "https:" + ankiPkg;
        }

        try {
            String fileName = UUID.randomUUID() + "." + MimeTypeMap.getFileExtensionFromUrl(audioUrl);
            File localFile = downloadFile(audioUrl, fileName);
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", localFile);

            context.grantUriPermission(ankiPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                String internalName = mAnkiDroid.getApi().addMediaFromUri(uri, fileName, "audio");
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
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.connect();

        File file = new File(context.getCacheDir(), fileName);
        try (InputStream input = new BufferedInputStream(url.openStream(), 8192);
                FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
        }
        return file;
    }

    private Long getDeckId() {
        Long did = mAnkiDroid.findDeckIdByName(DECK_NAME);
        if (did == null) {
            did = mAnkiDroid.getApi().addNewDeck(DECK_NAME);
            mAnkiDroid.storeDeckReference(DECK_NAME, did);
        }
        return did;
    }

    private Long getModelId(AbstractAnkiNote note) {
        Long mid = mAnkiDroid.findModelIdByName(
                note.getModelName(), note.getFieldNames().size());
        if (mid == null) {
            mid = mAnkiDroid
                    .getApi()
                    .addNewCustomModel(
                            note.getModelName(),
                            (String[]) note.getFieldNames().toArray(new String[] {}),
                            note.getCardNames(),
                            note.getQuestionTemplates(),
                            note.getAnswerTemplates(),
                            note.getCss(),
                            getDeckId(),
                            null);
            mAnkiDroid.storeModelReference(note.getModelName(), mid);
        }
        return mid;
    }

    private static void showSnackbar(MainActivity activity, String message, boolean isError) {
        activity.runOnUiThread(() -> {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
                int bgColor = isError ? R.color.error_red : R.color.anki_blue;
                snackbar.setBackgroundTint(ContextCompat.getColor(activity, bgColor));
                snackbar.setTextColor(ContextCompat.getColor(activity, R.color.white));
                snackbar.show();
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
