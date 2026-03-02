package com.github.lauroschuck.ankiquickadd.anki;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.github.lauroschuck.ankiquickadd.MainActivity;
import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.SettingsActivity;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.model.TranslationCard;
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
import java.util.Map;
import java.util.Set;

/**
 * Handles the integration with AnkiDroid for creating language flashcards.
 */
public class AnkiIntegration {

    private static final String TAG = "AnkiIntegration";
    private static final int AD_PERM_REQUEST = 0;
    private static final String DECK_NAME = "Anki Quick Add::Swedish-English";

    private final AnkiDroidHelper mAnkiDroid;
    private final MainActivity context;

    public AnkiIntegration(MainActivity context) {
        this.context = context;
        this.mAnkiDroid = new AnkiDroidHelper(context);
        if (mAnkiDroid.shouldRequestPermission()) {
            mAnkiDroid.requestPermission(context, AD_PERM_REQUEST);
        }
    }

    /**
     * Entry point to create Anki cards from a list of TranslationCard objects.
     * Operates in a background thread to handle network and file I/O.
     */
    public static void createAnkiCards(MainActivity context, List<TranslationCard> cards) {
        if (cards == null || cards.isEmpty()) {
            showSnackbar(context, "No cards selected.", true);
            return;
        }
        new Thread(() -> new AnkiIntegration(context).addCardsToAnkiDroid(cards)).start();
    }

    private void addCardsToAnkiDroid(final List<TranslationCard> data) {
        AnkiNote note = AnkiNote.SOURCE_TARGET_TEXT_V1;
        Long deckId = getDeckId();
        Long modelId = getModelId();

        if (deckId == null || modelId == null) {
            showSnackbar(context, "Card add failed: API Error", true);
            return;
        }

        String[] fieldNames = mAnkiDroid.getApi().getFieldList(modelId);
        if (fieldNames == null) {
            showSnackbar(context, "Card add failed: Model Error", true);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sourceLang = prefs.getString(SettingsActivity.KEY_SOURCE_LANGUAGE, Language.SWEDISH.getIsoCode());
        String targetLang = prefs.getString(SettingsActivity.KEY_TARGET_LANGUAGE, Language.ENGLISH.getIsoCode());

        LinkedList<String[]> fieldsList = new LinkedList<>();
        LinkedList<Set<String>> tagsList = new LinkedList<>();

        String ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        
        // Cache to avoid downloading/uploading the same audio multiple times in one batch
        Map<String, String> audioCache = new HashMap<>();

        for (TranslationCard card : data) {
            String[] fields = new String[fieldNames.length];

            // Mapping to SOURCE_TARGET_TEXT_V1 fields
            // 0: SourceText, 1: SourceLang, 2: TargetText, 3: TargetLang, 4: LexicalCat, 5: NoteHeader, 6: Notes, 7: HiddenNotes, 8: Audio, 9: SourceUrl
            fields[0] = card.sourceText();
            fields[1] = sourceLang;
            fields[2] = card.targetText();
            fields[3] = targetLang;
            fields[4] = card.lexicalCategory();
            fields[5] = card.headword();
            fields[6] = card.definition();
            fields[9] = String.format("https://%s.wiktionary.org/wiki/%s#Swedish", targetLang, card.headword());

            // Handle Audio with caching per headword
            String headword = card.headword();
            String soundTag = audioCache.get(headword);
            if (soundTag == null) {
                soundTag = processAudio(card, sourceLang, ankiPkg);
                if (!soundTag.isEmpty()) {
                    audioCache.put(headword, soundTag);
                }
            }
            fields[8] = soundTag;

            fieldsList.add(fields);
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

    /**
     * Downloads and imports the audio file into AnkiDroid's media collection.
     * Returns the internal name of the file wrapped in [sound:filename] syntax.
     */
    private String processAudio(TranslationCard card, String sourceLang, String ankiPkg) {
        String urlString = card.audioUrl();
        if (urlString == null || urlString.isEmpty() || ankiPkg == null) {
            return "";
        }

        if (urlString.startsWith("//")) {
            urlString = "https:" + urlString;
        }

        try {
            String fileName = sourceLang + "-" + card.headword() + "." + MimeTypeMap.getFileExtensionFromUrl(urlString);
            File localFile = downloadFile(urlString, fileName);
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
            Log.e(TAG, "Audio processing failed for: " + card.headword(), e);
        }
        return "";
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

    private Long getModelId() {
        AnkiNote note = AnkiNote.SOURCE_TARGET_TEXT_V1;
        Long mid = mAnkiDroid.findModelIdByName(note.getModelName(), note.getFieldNames().length);
        if (mid == null) {
            mid = mAnkiDroid.getApi().addNewCustomModel(
                    note.getModelName(),
                    note.getFieldNames(),
                    note.getCardNames(),
                    note.getQuestionTemplates(),
                    note.getAnswerTemplates(),
                    note.getCss(),
                    getDeckId(),
                    null
            );
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
