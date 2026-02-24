package com.github.lauroschuck.ankiquickadd;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.github.lauroschuck.ankiquickadd.model.Language;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_SOURCE_LANGUAGE = "source_language";
    public static final String KEY_TARGET_LANGUAGE = "target_language";

    private Spinner sourceSpinner;
    private Spinner targetSpinner;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sourceSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetSpinner = findViewById(R.id.targetLanguageSpinner);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ArrayAdapter<Language> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Language.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        sourceSpinner.setAdapter(adapter);
        targetSpinner.setAdapter(adapter);

        // Load saved values
        String sourceIso = prefs.getString(KEY_SOURCE_LANGUAGE, Language.SWEDISH.getIsoCode());
        String targetIso = prefs.getString(KEY_TARGET_LANGUAGE, Language.ENGLISH.getIsoCode());

        setSpinnerToValue(sourceSpinner, sourceIso);
        setSpinnerToValue(targetSpinner, targetIso);
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
        Language source = (Language) sourceSpinner.getSelectedItem();
        Language target = (Language) targetSpinner.getSelectedItem();

        prefs.edit()
                .putString(KEY_SOURCE_LANGUAGE, source.getIsoCode())
                .putString(KEY_TARGET_LANGUAGE, target.getIsoCode())
                .apply();
    }
}
