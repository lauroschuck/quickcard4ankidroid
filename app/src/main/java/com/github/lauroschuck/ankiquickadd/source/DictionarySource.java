package com.github.lauroschuck.ankiquickadd.source;

import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.model.TranslationCard;
import java.util.List;

/**
 * Interface for dictionary sources.
 */
public interface DictionarySource {
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
    interface OnCardsReadyListener {
        void onCardsReady(List<TranslationCard> cards);
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
     * Fetches more examples if available.
     * @param word the word to look up
     * @param learningLanguage the learning language
     * @param nativeLanguage the native language
     * @param page the page number to fetch
     * @param listener the listener to receive results (HTML fragment or JSON)
     */
    void fetchMore(
            String word, Language learningLanguage, Language nativeLanguage, int page, OnResultListener listener);

    /**
     * Returns the JavaScript code to extract card data or IDs from the rendered HTML.
     */
    String getExtractionJs();

    /**
     * Processes the selection from JavaScript and returns a list of TranslationCard objects.
     * @param json the JSON string returned by the JavaScript extraction code
     * @param listener the listener to receive the cards
     */
    void getCardsFromSelection(String json, OnCardsReadyListener listener);
}
