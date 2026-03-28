package com.github.lauroschuck.ankiquickadd.source;

import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.util.List;

/**
 * Interface for dictionary sources.
 */
public interface DictionarySource<N extends AbstractAnkiNote<I>, I extends AbstractAnkiNote.Input> {
    /**
     * Interface for listener to receive results.
     */
    interface OnResultListener {
        void onSuccess(String html, String headword);

        void onError(String message);
    }

    /**
     * Interface for listener to receive selected cards.
     */
    interface OnCardsReadyListener<I extends AbstractAnkiNote.Input> {
        void onCardsReady(
                Language learningLanguage, Language nativeLanguage, String audioUrl, String sourceUrl, List<I> inputs);
    }

    /**
     * Fetches definition or translation for a word.
     * @param word the word to look up
     * @param learningLanguage the learning language selected in settings
     * @param nativeLanguage the native language selected in settings
     * @param listener the listener to receive the results
     */
    void fetch(String word, Language learningLanguage, Language nativeLanguage, OnResultListener listener);

    /**
     * Returns the JavaScript code to extract card data or IDs from the rendered HTML.
     */
    String getExtractionJs();

    /**
     * Processes the selection from JavaScript and returns a list of TranslationCard objects.
     * @param json the JSON string returned by the JavaScript extraction code
     * @param listener the listener to receive the cards
     */
    void getCardsFromSelection(String json, OnCardsReadyListener<I> listener);
}
