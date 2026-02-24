package com.example.swedishanki;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

/**
 * Represents a single flashcard extracted from Wiktionary.
 * Using a record for concise data handling (available in Java 17+).
 */
public record SwedishCard(
    String swedish,
    String english,
    String definition,
    String grammaticalClass
) {
    /**
     * Utility method to parse JSON extraction data into a list of SwedishCard objects.
     */
    public static List<SwedishCard> fromJson(String json) {
        return new Gson().fromJson(json, new TypeToken<List<SwedishCard>>(){}.getType());
    }
}
