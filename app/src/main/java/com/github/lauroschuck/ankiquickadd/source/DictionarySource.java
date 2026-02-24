package com.github.lauroschuck.ankiquickadd.source;

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
     * Fetches definition for a word.
     * @param word the word to look up
     * @param listener the listener to receive the results
     */
    void fetch(String word, OnResultListener listener);
}
