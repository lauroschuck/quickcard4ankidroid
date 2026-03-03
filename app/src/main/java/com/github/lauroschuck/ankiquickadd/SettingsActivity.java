package com.github.lauroschuck.ankiquickadd;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.github.lauroschuck.ankiquickadd.model.Language;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_LEARNING_LANGUAGE = "learning_language";
    public static final String KEY_NATIVE_LANGUAGE = "native_language";

    private Spinner learningLanguageSpinner;
    private Spinner nativeLanguageSpinner;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        learningLanguageSpinner = findViewById(R.id.learningLanguageSpinner);
        nativeLanguageSpinner = findViewById(R.id.nativeLanguageSpinner);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ArrayAdapter<Language> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Language.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        learningLanguageSpinner.setAdapter(adapter);
        nativeLanguageSpinner.setAdapter(adapter);

        // Load saved values
        String learningIso = prefs.getString(KEY_LEARNING_LANGUAGE, Language.SWEDISH.getIsoCode());
        String nativeIso = prefs.getString(KEY_NATIVE_LANGUAGE, Language.ENGLISH.getIsoCode());

        setSpinnerToValue(learningLanguageSpinner, learningIso);
        setSpinnerToValue(nativeLanguageSpinner, nativeIso);
    }

    private void setSpinnerToValue(Spinner spinner, String isoCode) {
        Language[] languages = Language.values();
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].getIsoCode().equals(isoCode)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save values
        Language learningLang = (Language) learningLanguageSpinner.getSelectedItem();
        Language nativeLang = (Language) nativeLanguageSpinner.getSelectedItem();

        prefs.edit()
                .putString(KEY_LEARNING_LANGUAGE, learningLang.getIsoCode())
                .putString(KEY_NATIVE_LANGUAGE, nativeLang.getIsoCode())
                .apply();
    }
}
