package com.github.lauroschuck.ankiquickadd;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;
import lombok.NonNull;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_LEARNING_LANGUAGE = "learning_language";
    public static final String KEY_NATIVE_LANGUAGE = "native_language";

    private static final Language[] ALL_LANGUAGES = sortedLanguages(Language::values);
    private static final Language[] NATIVE_LANGUAGES = sortedLanguages(Language::valuesAvailableAsNative);

    private Spinner learningLanguageSpinner;
    private Spinner nativeLanguageSpinner;
    private SharedPreferences prefs;

    private static Language[] sortedLanguages(Supplier<Language[]> supplier) {
        var languages = supplier.get();
        Arrays.sort(languages, Comparator.comparing(Language::getDisplayName));
        return languages;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        learningLanguageSpinner = findViewById(R.id.learningLanguageSpinner);
        nativeLanguageSpinner = findViewById(R.id.nativeLanguageSpinner);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        learningLanguageSpinner.setAdapter(createArrayAdapter(ALL_LANGUAGES));
        nativeLanguageSpinner.setAdapter(createArrayAdapter(NATIVE_LANGUAGES));

        // Load saved values
        String learningIso = prefs.getString(KEY_LEARNING_LANGUAGE, Language.SV.getIsoCode());
        String nativeIso = prefs.getString(KEY_NATIVE_LANGUAGE, Language.EN.getIsoCode());

        setSpinnerToValue(learningLanguageSpinner, learningIso);
        setSpinnerToValue(nativeLanguageSpinner, nativeIso);
    }

    @NonNull
    private ArrayAdapter<Language> createArrayAdapter(Language[] languages) {
        var languageArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        languageArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return languageArrayAdapter;
    }

    private void setSpinnerToValue(Spinner spinner, String isoCode) {
        var position = ((ArrayAdapter) spinner.getAdapter()).getPosition(Language.ofIsoCode(isoCode));
        spinner.setSelection(position);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save values
        var learningLang = (Language) learningLanguageSpinner.getSelectedItem();
        var nativeLang = (Language) nativeLanguageSpinner.getSelectedItem();

        prefs.edit()
                .putString(KEY_LEARNING_LANGUAGE, learningLang.getIsoCode())
                .putString(KEY_NATIVE_LANGUAGE, nativeLang.getIsoCode())
                .apply();
    }
}
