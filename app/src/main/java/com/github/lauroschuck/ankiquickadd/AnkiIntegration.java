package com.github.lauroschuck.ankiquickadd;

import android.util.Log;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AnkiIntegration {

    private static final int AD_PERM_REQUEST = 0;
    private AnkiDroidHelper mAnkiDroid;
    private ProcessTextActivity context;

    public AnkiIntegration(ProcessTextActivity context) {
        this.context = context;
        mAnkiDroid = new AnkiDroidHelper(context);
        if (mAnkiDroid.shouldRequestPermission()) {
            mAnkiDroid.requestPermission(context, AD_PERM_REQUEST);
        }
    }

    public static void createAnkiCards(ProcessTextActivity context, List<TranslationCard> cards) {

        if (cards == null || cards.isEmpty()) {
            Toast.makeText(context, "No cards selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AnkiIntegration(context).addCardsToAnkiDroid(cards);

        Toast.makeText(context, "Successfully sent " + cards.size() + " cards to Anki!", Toast.LENGTH_SHORT).show();
    }

    /**
     * get the deck id
     *
     * @return might be null if there was a problem
     */
    private Long getDeckId() {
        Long did = mAnkiDroid.findDeckIdByName(AnkiDroidConfig.DECK_NAME);
        if (did == null) {
            did = mAnkiDroid.getApi().addNewDeck(AnkiDroidConfig.DECK_NAME);
            mAnkiDroid.storeDeckReference(AnkiDroidConfig.DECK_NAME, did);
        }
        return did;
    }

    /**
     * get model id
     *
     * @return might be null if there was an error
     */
    private Long getModelId() {
        Long mid = mAnkiDroid.findModelIdByName(AnkiDroidConfig.MODEL_NAME, AnkiDroidConfig.FIELDS.length);
        if (mid == null) {
            mid = mAnkiDroid.getApi().addNewCustomModel(AnkiDroidConfig.MODEL_NAME, AnkiDroidConfig.FIELDS,
                    AnkiDroidConfig.CARD_NAMES, AnkiDroidConfig.QFMT, AnkiDroidConfig.AFMT, AnkiDroidConfig.CSS, getDeckId(), null);
            mAnkiDroid.storeModelReference(AnkiDroidConfig.MODEL_NAME, mid);
        }
        return mid;
    }

    private void addCardsToAnkiDroid(final List<TranslationCard> data) {
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
            // Build a field map accounting for the fact that the user could have changed the fields in the model
            String[] flds = new String[fieldNames.length];
            
            // Mapping new terms:
            // Assuming flds order: [sourceText, headword, targetText, definition, lexicalCategory]
            // Adjusted based on your AnkiDroidConfig field order if available.
            flds[0] = card.sourceText();
            flds[1] = card.headword();
            flds[2] = card.targetText();
            flds[3] = card.definition();
            flds[4] = card.lexicalCategory();

            tags.add(AnkiDroidConfig.TAGS);
            fields.add(flds);
        }
        // Remove any duplicates from the LinkedLists and then add over the API
        mAnkiDroid.removeDuplicates(fields, tags, modelId);
        int added = mAnkiDroid.getApi().addNotes(modelId, deckId, fields, tags);

        if (added != 0) {
            Toast.makeText(context, "n_items_added "+ added, Toast.LENGTH_LONG).show();
        } else {
            // API indicates that a 0 return value is an error
            Toast.makeText(context, "card_add_fail", Toast.LENGTH_LONG).show();
        }
    }

}
