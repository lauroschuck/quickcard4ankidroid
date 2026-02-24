package com.github.lauroschuck.ankiquickadd.model;

public enum Language {
    ENGLISH("English", "en"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    SWEDISH("Swedish", "sv"),
    NORWEGIAN("Norwegian", "no"),
    DANISH("Danish", "da"),
    PORTUGUESE("Portuguese", "pt"),
    ITALIAN("Italian", "it"),
    ARABIC("Arabic", "ar"),
    CHINESE("Chinese", "zh"),
    JAPANESE("Japanese", "ja");

    private final String displayName;
    private final String isoCode;

    Language(String displayName, String isoCode) {
        this.displayName = displayName;
        this.isoCode = isoCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIsoCode() {
        return isoCode;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
