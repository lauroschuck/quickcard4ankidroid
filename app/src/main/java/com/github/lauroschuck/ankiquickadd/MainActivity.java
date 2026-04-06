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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.NonNull;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

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

        setupSourceSpinner();
        setupBackPressed();

        settingsButton.setOnClickListener(v -> navigateToSettings());

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        viewModel.setLastUsedLearningLanguage(
                getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV));
        viewModel.setLastUsedNativeLanguage(
                getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN));

        handleIntent(getIntent());
    }

    public void navigateToSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void markWordAsProcessed(String word) {
        viewModel.getWordRepository().markWordAsProcessed(word);
    }

    public void removeEnqueuedWord(String word) {
        viewModel.getWordRepository().removeEnqueuedWord(word);
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
                var history = viewModel.getNavigationManager().getWordHistory();
                if (!history.isEmpty()) {
                    history.pop(); // Remove current word
                    if (!history.isEmpty()) {
                        fetchDefinition(history.peek(), true);
                        return;
                    }
                }

                if (viewModel.getNavigationManager().isRootIsSearch()
                        && getCurrentFragment() instanceof DefinitionFragment) {
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
            var word = viewModel.getNavigationManager().getCurrentWord().getValue();
            if (word != null && !word.isEmpty()) {
                Timber.d("Language changed, refetching word: %s", word);
                fetchDefinition(word, true);
            }
            viewModel.setLastUsedLearningLanguage(currentLearning);
            viewModel.setLastUsedNativeLanguage(currentNative);
        }

        viewModel.getWordRepository().reload();
    }

    private void setupSourceSpinner() {
        var availableSources = viewModel.getDictionaryRepository().getSources();

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

        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewModel.getDictionaryRepository().selectSource(position);
                Timber.d(
                        "Source selected: %s",
                        viewModel.getDictionaryRepository().getCurrentSource().getName());
                viewModel.getNavigationManager().setSearchWarning(null);
                var word = viewModel.getNavigationManager().getCurrentWord().getValue();
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
            Timber.d("Received word via PROCESS_TEXT: %s", selectedWord);
            viewModel.getNavigationManager().setRootIsSearch(false);
            fetchDefinition(selectedWord.toLowerCase(Locale.ROOT), false);
        } else {
            viewModel.getNavigationManager().setRootIsSearch(true);
            showSearchFragment(null);
        }
    }

    public void showSearchFragment(String warning) {
        viewModel.getNavigationManager().setCurrentWord("");
        viewModel.getNavigationManager().getWordHistory().clear();
        viewModel.getNavigationManager().setSearchWarning(warning);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new SearchFragment())
                .commit();
    }

    public void closeDefinition() {
        viewModel.getNavigationManager().setRootIsSearch(true);
        showSearchFragment(null);
    }

    public void fetchDefinition(@NonNull String word) {
        fetchDefinition(word, false);
    }

    public void fetchDefinition(@NonNull String word, boolean isFromHistory) {
        Timber.d("Fetching definition for: %s (isFromHistory=%b)", word, isFromHistory);
        var navManager = viewModel.getNavigationManager();
        if (!isFromHistory
                && (navManager.getWordHistory().isEmpty()
                        || !navManager.getWordHistory().peek().equals(word))) {
            navManager.getWordHistory().push(word);
        }

        navManager.setCurrentWord(word);
        navManager.setSearchWarning(null);

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

        var currentSource = viewModel.getDictionaryRepository().getCurrentSource();
        if (currentSource != null) {
            currentSource.fetch(word, learningLanguage, nativeLanguage, new DictionarySource.OnResultListener() {
                @Override
                public void onSuccess(String html, String headword) {
                    Timber.d("Successfully fetched definition for: %s", word);
                    runOnUiThread(() -> {
                        Fragment current = getCurrentFragment();
                        if (current instanceof DefinitionFragment) {
                            ((DefinitionFragment) current).loadHtml(html);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    Timber.w("Error fetching definition for %s: %s", word, message);
                    var lowercaseWord = word.toLowerCase(Locale.ROOT);
                    if (!word.equals(lowercaseWord)) {
                        Timber.d("Retrying with lowercase word: %s", lowercaseWord);
                        fetchDefinition(lowercaseWord, true);
                    } else {
                        runOnUiThread(() -> {
                            if (!navManager.getWordHistory().isEmpty()
                                    && navManager.getWordHistory().peek().equals(word)) {
                                navManager.getWordHistory().pop();
                            }
                            showSearchFragment("Word not found: " + word);
                        });
                    }
                }
            });
        } else {
            Timber.e("No current source selected when fetching definition for: %s", word);
        }
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
