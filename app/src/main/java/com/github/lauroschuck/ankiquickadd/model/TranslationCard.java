package com.github.lauroschuck.ankiquickadd.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

/**
 * Represents a single flashcard extracted from Wiktionary.
 * Using a record for concise data handling (available in Java 17+).
 */
public record TranslationCard(
    String headword,
    String learningText,
    String nativeText,
    String definition,
    String lexicalCategory,
    String audioUrl
) {
    /**
     * Utility method to parse JSON extraction data into a list of TranslationCard objects.
     */
    public static List<TranslationCard> fromJson(String json) {
        return new Gson().fromJson(json, new TypeToken<List<TranslationCard>>(){}.getType());
    }
}
