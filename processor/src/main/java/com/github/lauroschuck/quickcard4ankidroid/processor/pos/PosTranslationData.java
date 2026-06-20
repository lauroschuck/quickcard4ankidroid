package com.github.lauroschuck.quickcard4ankidroid.processor.pos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class PosTranslationData {

    private static final String UNKNOWN_KEY = "unknown";

    private final Map<String, Map<String, String>> translations;

    @Deprecated
    public Map<String, Map<String, String>> getTranslations() {
        return translations;
    }

    public PosTranslationData() {
        this(Map.of());
    }

    public static PosTranslationData read(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Map<String, Map<String, Map<String, String>>> map = gson.fromJson(reader, new TypeToken<Map<String, Map<String, Map<String, String>>>>() {
            }.getType());
            return new PosTranslationData(map.get("translations"));
        }
    }

    public void write(File file, boolean override) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (file.exists() && !override) {
            throw new IllegalStateException("Override is not allowed but output file exists: " + file);
        }
        try (FileWriter writer = new FileWriter(file, !override)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            throw new IOException("Failed to write file: " + file, e);
        }
    }

    private PosTranslationData(Map<String, Map<String, String>> translations) {
        this.translations = new TreeMap<>();
        translations.forEach((lang, map) -> {
            Map<String, String> mapCopy = new TreeMap<>(map);
            mapCopy.remove(UNKNOWN_KEY);
            this.translations.put(lang, mapCopy);
        });
    }

    public synchronized String getTranslation(String language, String pos) {
        Map<String, String> map = translations.get(language);
        if (map == null) {
            throw new IllegalStateException(String.format("No translations for '%s'", language));
        }
        return map.get(pos);
    }

    public int countBlanks(String language) {
        Map<String, String> translationMap = translations.get(language);
        if (translationMap == null) {
            throw new IllegalArgumentException(String.format("Non existing language '%s'", language));
        }
        return Math.toIntExact(translationMap.entrySet().stream().filter(e -> e.getValue().isEmpty()).count());
    }

    public synchronized void setTranslation(String language, String pos, String translation) {
        if (pos.equals(UNKNOWN_KEY)) {
            return;
        }
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(translation, "translation");
        String trimmedTranslation = translation.trim();
        if (trimmedTranslation.isEmpty()) {
            throw new IllegalArgumentException("Blank translation");
        }
        Map<String, String> translationMap = translations.get(language);
        if (translationMap == null) {
            throw new IllegalArgumentException(String.format("Non existing language '%s'", language));
        }
        translationMap.compute(pos, (k, v) -> {
            if (v == null|| !v.isEmpty()) {
                throw new IllegalArgumentException(String.format("Key '%s' was expected to be empty but it is '%s' for language '%s'", pos, v, language));
            } else {
                return trimmedTranslation;
            }
        });
    }
}
