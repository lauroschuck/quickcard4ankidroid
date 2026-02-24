package com.github.lauroschuck.ankiquickadd.source;

import com.github.lauroschuck.ankiquickadd.model.Language;

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
     * Fetches definition or translation for a word.
     * @param word the word to look up
     * @param sourceLanguage the source language selected in settings
     * @param targetLanguage the target language selected in settings
     * @param listener the listener to receive the results
     */
    void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener);

    /**
     * Fetches more examples if available.
     * @param word the word to look up
     * @param sourceLanguage the source language
     * @param targetLanguage the target language
     * @param page the page number to fetch
     * @param listener the listener to receive results (HTML fragment or JSON)
     */
    void fetchMore(String word, Language sourceLanguage, Language targetLanguage, int page, OnResultListener listener);

    /**
     * Returns the JavaScript code to extract card data from the rendered HTML.
     */
    String getExtractionJs();
}
