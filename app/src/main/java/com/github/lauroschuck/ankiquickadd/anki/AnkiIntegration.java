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

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public static void createAnkiCards(MainActivity context, List<TranslationCard> cards, boolean isDefinitions) {
        if (cards == null || cards.isEmpty()) {
            showSnackbar(context, "No cards selected.", true);
            return;
        }
        new Thread(() -> new AnkiIntegration(context).addCardsToAnkiDroid(cards, isDefinitions)).start();
    }

    private void addCardsToAnkiDroid(final List<TranslationCard> data, boolean isDefinitions) {
        AnkiNote note = isDefinitions ? AnkiNote.DICTIONARY_DEFINITION : AnkiNote.LEARNING_NATIVE_TEXT;
        Long deckId = getDeckId();
        Long modelId = getModelId(note);

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
        String learningLang = prefs.getString(SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV.getIsoCode());
        String nativeLang = prefs.getString(SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN.getIsoCode());

        LinkedList<String[]> fieldsList = new LinkedList<>();
        LinkedList<Set<String>> tagsList = new LinkedList<>();
        String ankiPkg = AddContentApi.getAnkiDroidPackageName(context);
        Map<String, String> audioCache = new HashMap<>();

        if (isDefinitions) {
            // Group cards by headword and lexical category
            Map<String, List<TranslationCard>> groups = new LinkedHashMap<>();
            for (TranslationCard card : data) {
                String key = card.headword() + "|" + card.lexicalCategory();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(card);
            }

            for (List<TranslationCard> group : groups.values()) {
                String[] fields = new String[fieldNames.length];
                TranslationCard first = group.get(0);
                
                // Fields mapping for DICTIONARY_DEFINITION:
                // 0: Id, 1: LearningWord, 2: LearningLang, 3: LexicalCat, 4: NativeLang
                fields[0] = String.format("%s-%s-%s", learningLang, nativeLang, first.headword());
                fields[1] = first.headword();
                fields[2] = learningLang;
                fields[3] = first.lexicalCategory();
                fields[4] = nativeLang;

                // Definitions and their examples (up to 5)
                // Each definition uses 5 fields: Def, Learning, AltLearning, Native, AltNative
                for (int i = 0; i < Math.min(group.size(), AnkiNote.InternalHelper.DICTIONARY_DEFINITION_COUNT); i++) {
                    TranslationCard card = group.get(i);
                    int baseIdx = 5 + (i * 5);
                    fields[baseIdx] = cleanHtml(card.definition());
                    fields[baseIdx + 1] = cleanHtml(card.learningText());
                    fields[baseIdx + 2] = ""; // AltLearningText
                    fields[baseIdx + 3] = cleanHtml(card.nativeText());
                    fields[baseIdx + 4] = ""; // AltNativeText
                }

                // NoteHeader, Notes, HiddenNotes, Audio, SourceUrl follow the definitions
                int offset = 5 + (AnkiNote.InternalHelper.DICTIONARY_DEFINITION_COUNT * 5);
                fields[offset] = first.headword(); // NoteHeader
                fields[offset + 4] = String.format("https://%s.wiktionary.org/wiki/%s#%s",
                        nativeLang, first.headword(), learningLang); // SourceUrl (pseudo-anchor)

                String headword = first.headword();
                String soundTag = audioCache.get(headword);
                if (soundTag == null) {
                    soundTag = processAudio(first, learningLang, ankiPkg);
                    if (!soundTag.isEmpty()) audioCache.put(headword, soundTag);
                }
                fields[offset + 3] = soundTag; // Audio

                sanitizeFields(fields);
                fieldsList.add(fields);
                tagsList.add(note.getTags());
            }
        } else {
            for (TranslationCard card : data) {
                String[] fields = new String[fieldNames.length];
                // Fields mapping for LEARNING_NATIVE_TEXT:
                // 0: LearningText, 1: AltLearningText, 2: LearningLang, 3: NativeText, 4: AltNativeText, 
                // 5: NativeLang, 6: LexicalCat, 7: NoteHeader, 8: Notes, 9: HiddenNotes, 10: Audio, 11: SourceUrl
                fields[0] = cleanHtml(card.learningText());
                fields[1] = ""; // AltLearningText
                fields[2] = learningLang;
                fields[3] = cleanHtml(card.nativeText());
                fields[4] = ""; // AltNativeText
                fields[5] = nativeLang;
                fields[6] = card.lexicalCategory();
                fields[7] = card.headword();
                fields[8] = cleanHtml(card.definition());
                fields[11] = String.format("https://%s.wiktionary.org/wiki/%s#%s", 
                        nativeLang, card.headword(), learningLang);

                String headword = card.headword();
                String soundTag = audioCache.get(headword);
                if (soundTag == null) {
                    soundTag = processAudio(card, learningLang, ankiPkg);
                    if (!soundTag.isEmpty()) audioCache.put(headword, soundTag);
                }
                fields[10] = soundTag;

                sanitizeFields(fields);
                fieldsList.add(fields);
                tagsList.add(note.getTags());
            }
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

    private String processAudio(TranslationCard card, String learningLang, String ankiPkg) {
        String urlString = card.audioUrl();
        if (urlString == null || urlString.isEmpty() || ankiPkg == null) return "";
        if (urlString.startsWith("//")) urlString = "https:" + urlString;

        try {
            String fileName = learningLang + "-" + card.headword() + "." + MimeTypeMap.getFileExtensionFromUrl(urlString);
            File localFile = downloadFile(urlString, fileName);
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", localFile);

            context.grantUriPermission(ankiPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                String internalName = mAnkiDroid.getApi().addMediaFromUri(uri, fileName, "audio");
                if (internalName != null) return internalName;
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

    private Long getModelId(AnkiNote note) {
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
