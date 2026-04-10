package com.github.lauroschuck.ankiquickadd.source;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;
import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.DictionaryNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;

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
     * Returns the display name of this source.
     */
    String getName();

    default void setContext(@NonNull Context context) {
        // default no-op
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
     * Processes the selection from JavaScript and returns a SelectedCards object.
     * @param json the JSON string returned by the JavaScript extraction code
     */
    SelectedCards<? extends AbstractAnkiNote.Input> getCardsFromSelection(String json);

    /**
     * Handles custom actions triggered from the WebView.
     * @param url the custom URL starting with app://source/
     * @param webView the WebView instance
     */
    default void handleSourceAction(String url, WebView webView) {
        // Default no-op
    }

    record SelectedDictionaryCards(
            Language learningLanguage,
            Language nativeLanguage,
            Uri audioUrl,
            Uri sourceUrl,
            List<DictionaryNote.Input> inputs)
            implements SelectedCards<DictionaryNote.Input> {

        public SelectedDictionaryCards {
            var categories =
                    inputs.stream().map(DictionaryNote.Input::lexicalCategory).collect(Collectors.toList());
            if (categories.size() != new HashSet<>(categories).size()) {
                throw new IllegalArgumentException("Duplicate lexical categories found: " + categories);
            }
        }
    }

    record SelectedTextCards(
            Language learningLanguage,
            Language nativeLanguage,
            Uri audioUrl,
            Uri sourceUrl,
            List<TextNote.Input> inputs)
            implements SelectedCards<TextNote.Input> {}

    sealed interface SelectedCards<I extends AbstractAnkiNote.Input> {
        Language learningLanguage();

        Language nativeLanguage();

        Uri audioUrl();

        Uri sourceUrl();

        List<I> inputs();
    }
}
