package com.github.lauroschuck.quickcard4ankidroid.model;

import androidx.annotation.Keep;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Keep
@EqualsAndHashCode
public class Language {

    private final Locale locale;

    private Language(@NonNull String language) {
        locale = new Locale(language);
    }

    public static Language ofIsoCode(@NonNull String language) {
        return new Language(language);
    }

    public String getDisplayName() {
        return getDisplayName(Locale.ENGLISH);
    }

    public String getDisplayName(Language inLanguage) {
        return getDisplayName(inLanguage.locale);
    }

    private String getDisplayName(Locale inLanguage) {
        var response = locale.getDisplayLanguage(inLanguage);
        return response.equals(getIsoCode()) ? locale.getDisplayLanguage() : response;
    }

    public String getIsoCode() {
        return locale.getLanguage();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
