package com.github.lauroschuck.ankiquickadd;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.lauroschuck.ankiquickadd.anki.notes.CardAssets;
import com.github.lauroschuck.ankiquickadd.anki.notes.DictionaryNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import lombok.NonNull;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AnkiQuickAdd";
    private Spinner sourceSpinner;
    private MainViewModel viewModel;

    private CardAssets cardAssets;
    private DictionaryNote dictionaryNote;
    private TextNote textNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        cardAssets = new CardAssets(getApplicationContext());
        dictionaryNote = new DictionaryNote(cardAssets);
        textNote = new TextNote(cardAssets);

        sourceSpinner = findViewById(R.id.sourceSpinner);
        var settingsButton = findViewById(R.id.settingsButton);

        setupSources();
        setupBackPressed();

        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        viewModel.setLastUsedLearningLanguage(
                getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV));
        viewModel.setLastUsedNativeLanguage(
                getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN));

        handleIntent(getIntent());
    }

    public DictionaryNote getDictionaryNote() {
        return dictionaryNote;
    }

    public TextNote getTextNote() {
        return textNote;
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!viewModel.getWordHistory().isEmpty()) {
                    viewModel.getWordHistory().pop(); // Remove current word
                    if (!viewModel.getWordHistory().isEmpty()) {
                        fetchDefinition(viewModel.getWordHistory().peek(), true);
                        return;
                    }
                }

                if (viewModel.isRootIsSearch() && getCurrentFragment() instanceof DefinitionFragment) {
                    showSearchFragment(null);
                } else {
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var currentLearning = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV);
        var currentNative = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN);

        if (currentLearning != viewModel.getLastUsedLearningLanguage()
                || currentNative != viewModel.getLastUsedNativeLanguage()) {
            var word = viewModel.getCurrentWord().getValue();
            if (word != null && !word.isEmpty()) {
                fetchDefinition(word, true);
            }
            viewModel.setLastUsedLearningLanguage(currentLearning);
            viewModel.setLastUsedNativeLanguage(currentNative);
        }
    }

    private void setupSources() {
        var availableSources = new ArrayList<DictionarySource>();
        ServiceLoader<DictionarySource> loader = ServiceLoader.load(DictionarySource.class);
        for (DictionarySource source : loader) {
            source.setContext(this);
            availableSources.add(source);
        }

        viewModel.getSources().clear();
        viewModel.getSources().addAll(availableSources);

        if (availableSources.size() <= 1) {
            sourceSpinner.setVisibility(View.GONE);
        } else {
            sourceSpinner.setVisibility(View.VISIBLE);
            List<String> sourceNames =
                    availableSources.stream().map(DictionarySource::getName).collect(Collectors.toList());
            var adapter = new ArrayAdapter<>(this, R.layout.spinner_item, sourceNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceSpinner.setAdapter(adapter);
        }

        viewModel.setCurrentSource(viewModel.getSources().get(0));

        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewModel.setCurrentSource(viewModel.getSources().get(position));
                viewModel.setSearchWarning(null);
                var word = viewModel.getCurrentWord().getValue();
                if (word != null && !word.isEmpty()) {
                    fetchDefinition(word, true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void handleIntent(Intent intent) {
        var selectedWord = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        if (selectedWord != null) {
            viewModel.setRootIsSearch(false);
            fetchDefinition(selectedWord.toLowerCase(Locale.ROOT), false);
        } else {
            viewModel.setRootIsSearch(true);
            showSearchFragment(null);
        }
    }

    public void showSearchFragment(String warning) {
        viewModel.setCurrentWord("");
        viewModel.getWordHistory().clear();
        viewModel.setSearchWarning(warning);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new SearchFragment())
                .commit();
    }

    public void closeDefinition() {
        viewModel.setRootIsSearch(true);
        showSearchFragment(null);
    }

    public void fetchDefinition(@NonNull String word) {
        fetchDefinition(word, false);
    }

    public void fetchDefinition(@NonNull String word, boolean isFromHistory) {
        if (!isFromHistory
                && (viewModel.getWordHistory().isEmpty()
                        || !viewModel.getWordHistory().peek().equals(word))) {
            viewModel.getWordHistory().push(word);
        }

        viewModel.setCurrentWord(word);
        viewModel.setSearchWarning(null);

        // Ensure we are in DefinitionFragment
        if (!(getCurrentFragment() instanceof DefinitionFragment)) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new DefinitionFragment())
                    .commitNow();
        }

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var learningLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV);
        var nativeLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN);

        viewModel
                .getCurrentSource()
                .fetch(word, learningLanguage, nativeLanguage, new DictionarySource.OnResultListener() {
                    @Override
                    public void onSuccess(String html, String headword) {
                        runOnUiThread(() -> {
                            Fragment current = getCurrentFragment();
                            if (current instanceof DefinitionFragment) {
                                ((DefinitionFragment) current).loadHtml(html);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        var lowercaseWord = word.toLowerCase(Locale.ROOT);
                        if (!word.equals(lowercaseWord)) {
                            fetchDefinition(lowercaseWord, true);
                        } else {
                            runOnUiThread(() -> {
                                if (!viewModel.getWordHistory().isEmpty()
                                        && viewModel.getWordHistory().peek().equals(word)) {
                                    viewModel.getWordHistory().pop();
                                }
                                showSearchFragment("Word not found: " + word);
                            });
                        }
                    }
                });
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
    }

    private Language getLanguageFromPref(SharedPreferences prefs, String key, Language defaultLang) {
        var iso = prefs.getString(key, defaultLang.getIsoCode());
        for (Language l : Language.values()) {
            if (l.getIsoCode().equals(iso)) return l;
        }
        return defaultLang;
    }

    public void showSnackbar(String message, boolean isError) {
        // Implement snackbar logic if needed, or use Toast
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
