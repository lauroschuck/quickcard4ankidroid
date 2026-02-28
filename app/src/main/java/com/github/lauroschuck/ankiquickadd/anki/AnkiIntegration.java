package com.github.lauroschuck.ankiquickadd.anki;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.github.lauroschuck.ankiquickadd.MainActivity;
import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.SettingsActivity;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.model.TranslationCard;
import com.google.android.material.snackbar.Snackbar;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AnkiIntegration {

    private static final int AD_PERM_REQUEST = 0;
    private static final String DECK_NAME = "Anki Quick Add::Swedish-English"; // Default deck name
    private AnkiDroidHelper mAnkiDroid;
    private MainActivity context;

    public AnkiIntegration(MainActivity context) {
        this.context = context;
        mAnkiDroid = new AnkiDroidHelper(context);
        if (mAnkiDroid.shouldRequestPermission()) {
            mAnkiDroid.requestPermission(context, AD_PERM_REQUEST);
        }
    }

    public static void createAnkiCards(MainActivity context, List<TranslationCard> cards) {
        if (cards == null || cards.isEmpty()) {
            showSnackbar(context, "No cards selected.", true);
            return;
        }
        new AnkiIntegration(context).addCardsToAnkiDroid(cards);
    }

    private static void showSnackbar(MainActivity activity, String message, boolean isError) {
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
    }

    /**
     * get the deck id
     *
     * @return might be null if there was a problem
     */
    private Long getDeckId() {
        Long did = mAnkiDroid.findDeckIdByName(DECK_NAME);
        if (did == null) {
            did = mAnkiDroid.getApi().addNewDeck(DECK_NAME);
            mAnkiDroid.storeDeckReference(DECK_NAME, did);
        }
        return did;
    }

    /**
     * get model id
     *
     * @return might be null if there was an error
     */
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

    private void addCardsToAnkiDroid(final List<TranslationCard> data) {
        AnkiNote note = AnkiNote.SOURCE_TARGET_TEXT_V1;
        Long deckId = getDeckId();
        Long modelId = getModelId();
        if ((deckId == null) || (modelId == null)) {
            // we had an API error, report failure and return
            showSnackbar(context, "Card add failed: API Error", true);
            return;
        }
        String[] fieldNames = mAnkiDroid.getApi().getFieldList(modelId);
        Log.i("tag","field names " + (fieldNames != null ? List.of(fieldNames) : "null"));
        if (fieldNames == null) {
            // we had an API error, report failure and return
            showSnackbar(context, "Card add failed: Model Error", true);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sourceLang = prefs.getString(SettingsActivity.KEY_SOURCE_LANGUAGE, Language.SWEDISH.getIsoCode());
        String targetLang = prefs.getString(SettingsActivity.KEY_TARGET_LANGUAGE, Language.ENGLISH.getIsoCode());

        // Build list of fields and tags
        LinkedList<String[]> fields = new LinkedList<>();
        LinkedList<Set<String>> tags = new LinkedList<>();
        for (var card : data) {
            String[] flds = new String[fieldNames.length];
            
            // Mapping to SOURCE_TARGET_TEXT_V1 fields
            // 0: SourceText, 1: SourceLang, 2: TargetText, 3: TargetLang, 4: LexicalCat, 5: NoteHeader, 6: Notes, 7: HiddenNotes, 8: Audio, 9: SourceUrl
            if (flds.length >= 10) {
                flds[0] = card.sourceText();
                flds[1] = sourceLang;
                flds[2] = card.targetText();
                flds[3] = targetLang;
                flds[4] = card.lexicalCategory();
                flds[5] = card.headword();
                flds[6] = card.definition();
                // TODO #Swedish needs fixing
                flds[9] = String.format("https://%s.wiktionary.org/wiki/%s#Swedish", targetLang, card.headword());
            } else {
                // we had an API error, report failure and return;
            }

            tags.add(note.getTags());
            fields.add(flds);
        }
        // Remove any duplicates from the LinkedLists and then add over the API
        mAnkiDroid.removeDuplicates(fields, tags, modelId);
        int added = mAnkiDroid.getApi().addNotes(modelId, deckId, fields, tags);

        if (added != 0) {
            showSnackbar(context, "Successfully sent " + added + " cards to Anki", false);
        } else {
            // API indicates that a 0 return value is an error
            showSnackbar(context, "Card add failed: No notes added", true);
        }
    }

}
