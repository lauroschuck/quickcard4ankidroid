package com.github.lauroschuck.ankiquickadd.anki;

import android.util.Log;
import android.widget.Toast;

import com.github.lauroschuck.ankiquickadd.MainActivity;
import com.github.lauroschuck.ankiquickadd.model.TranslationCard;

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
            Toast.makeText(context, "No cards selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AnkiIntegration(context).addCardsToAnkiDroid(cards);
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
        AnkiNote note = AnkiNote.SAMPLE;
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
        AnkiNote note = AnkiNote.SAMPLE;
        Long deckId = getDeckId();
        Long modelId = getModelId();
        if ((deckId == null) || (modelId == null)) {
            // we had an API error, report failure and return
            Toast.makeText(context, "card_add_fail", Toast.LENGTH_LONG).show();
            return;
        }
        String[] fieldNames = mAnkiDroid.getApi().getFieldList(modelId);
        Log.i("tag","field names " + (fieldNames != null ? List.of(fieldNames) : "null"));
        if (fieldNames == null) {
            // we had an API error, report failure and return
            Toast.makeText(context, "card_add_fail", Toast.LENGTH_LONG).show();
            return;
        }
        // Build list of fields and tags
        LinkedList<String[]> fields = new LinkedList<>();
        LinkedList<Set<String>> tags = new LinkedList<>();
        for (var card : data) {
            String[] flds = new String[fieldNames.length];
            
            // Mapping to SAMPLE fields based on index
            // 0: Expression, 1: Reading, 2: Meaning, 3: Furigana, 4: Grammar, 5: Sentence, 6: SentenceFurigana, 7: SentenceMeaning
            if (flds.length >= 5) {
                flds[0] = card.sourceText();
                flds[1] = card.headword();
                flds[2] = card.targetText();
                flds[3] = card.definition();
                flds[4] = card.lexicalCategory();
            }

            tags.add(note.getTags());
            fields.add(flds);
        }
        // Remove any duplicates from the LinkedLists and then add over the API
        mAnkiDroid.removeDuplicates(fields, tags, modelId);
        int added = mAnkiDroid.getApi().addNotes(modelId, deckId, fields, tags);

        if (added != 0) {
            Toast.makeText(context, "Successfully sent " + added + " cards to Anki", Toast.LENGTH_SHORT).show();
        } else {
            // API indicates that a 0 return value is an error
            Toast.makeText(context, "card_add_fail", Toast.LENGTH_LONG).show();
        }
    }

}
