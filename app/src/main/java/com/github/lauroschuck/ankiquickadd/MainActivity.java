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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.lauroschuck.ankiquickadd.anki.notes.CardAssets;
import com.github.lauroschuck.ankiquickadd.anki.notes.DictionaryNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import com.google.android.material.navigation.NavigationView;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.NonNull;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private Spinner sourceSpinner;
    private MainViewModel viewModel;

    private DictionaryNote dictionaryNote;
    private TextNote textNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        var cardAssets = new CardAssets(getApplicationContext());
        dictionaryNote = new DictionaryNote(cardAssets);
        textNote = new TextNote(cardAssets);

        drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        sourceSpinner = findViewById(R.id.sourceSpinner);
        var menuButton = findViewById(R.id.menuButton);

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                navigateToSettings();
            } else if (id == R.id.nav_feedback) {
                showFeedbackDialog();
            } else if (id == R.id.nav_about) {
                showAboutDialog();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        setupSourceSpinner();
        setupBackPressed();

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        viewModel.setLastUsedLearningLanguage(getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE));
        viewModel.setLastUsedNativeLanguage(getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE));

        FirebaseHelper.setUserLanguages(viewModel.getLastUsedLearningLanguage(), viewModel.getLastUsedNativeLanguage());

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void showAboutDialog() {
        new AboutDialogFragment().show(getSupportFragmentManager(), AboutDialogFragment.TAG);
    }

    private void showFeedbackDialog() {
        new FeedbackDialogFragment().show(getSupportFragmentManager(), FeedbackDialogFragment.TAG);
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
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }

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
        var currentLearning = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE);
        var currentNative = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE);

        if (currentLearning != null
                && currentNative != null
                && (!currentLearning.equals(viewModel.getLastUsedLearningLanguage())
                        || !currentNative.equals(viewModel.getLastUsedNativeLanguage()))) {
            var word = viewModel.getNavigationManager().getCurrentWord().getValue();
            if (word != null && !word.isEmpty()) {
                Timber.d("Language changed, refetching word: %s", word);
                fetchDefinition(word, true);
            }
            viewModel.setLastUsedLearningLanguage(currentLearning);
            viewModel.setLastUsedNativeLanguage(currentNative);
            FirebaseHelper.setUserLanguages(currentLearning, currentNative);
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
                var repo = viewModel.getDictionaryRepository();
                var selectedSource = repo.getSources().get(position);
                var currentSource = repo.getCurrentSource();

                if (currentSource != null && currentSource.getName().equals(selectedSource.getName())) {
                    return;
                }

                repo.selectSource(position);
                Timber.d(
                        "Source selected: %s",
                        viewModel.getDictionaryRepository().getCurrentSource().getName());
                viewModel.getNavigationManager().setSearchWarning(null);
                var word = viewModel.getNavigationManager().getCurrentWord().getValue();
                if (word != null && !word.isEmpty() && getCurrentFragment() instanceof DefinitionFragment) {
                    fetchDefinition(word, true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        CharSequence text = null;
        FirebaseHelper.SearchMethod searchMethod = null;

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            searchMethod = FirebaseHelper.SearchMethod.SHARE;
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            searchMethod = FirebaseHelper.SearchMethod.CONTEXT_MENU;
        }

        if (text != null) {
            String word = text.toString().trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty()) {
                Timber.d("Received word via %s: %s", action, word);
                viewModel.getNavigationManager().setRootIsSearch(false);
                fetchDefinition(word);
                FirebaseHelper.logSearch(word, searchMethod);
                return;
            }
        }

        // If it's the main entry and we already have a fragment, don't reset to search screen.
        if (Intent.ACTION_MAIN.equals(action) && getCurrentFragment() != null) {
            return;
        }

        // Show search fragment by default.
        showSearchFragment(null);
    }

    public void showSearchFragment(String warning) {
        viewModel.getNavigationManager().setRootIsSearch(true);
        viewModel.getNavigationManager().setCurrentWord("");
        viewModel.getNavigationManager().getWordHistory().clear();
        viewModel.getNavigationManager().setSearchWarning(warning);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new SearchFragment())
                .commitNow();
    }

    public void closeDefinition() {
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
        navManager.setCurrentHtml("");
        navManager.setSearchWarning(null);

        // Ensure we are in DefinitionFragment
        if (!(getCurrentFragment() instanceof DefinitionFragment)) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new DefinitionFragment())
                    .commitNow();
        }

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var learningLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE);
        var nativeLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE);

        if (learningLanguage == null || nativeLanguage == null) {
            showSearchFragment(getString(R.string.error_select_dictionary));
            return;
        }

        var currentSource = viewModel.getDictionaryRepository().getCurrentSource();
        if (currentSource != null) {
            currentSource.fetch(word, learningLanguage, nativeLanguage, new DictionarySource.OnResultListener() {
                @Override
                public void onSuccess(String html, String headword) {
                    Timber.d("Successfully fetched definition for: %s", word);
                    FirebaseHelper.logFetchDefinition(word, true);
                    navManager.setCurrentHtml(html);
                }

                @Override
                public void onError(String message) {
                    Timber.w("Error fetching definition for %s: %s", word, message);
                    var lowercaseWord = word.toLowerCase(Locale.ROOT);
                    if (!word.equals(lowercaseWord)) {
                        Timber.d("Retrying with lowercase word: %s", lowercaseWord);
                        fetchDefinition(lowercaseWord, true);
                    } else {
                        FirebaseHelper.logFetchDefinition(word, false);
                        runOnUiThread(() -> {
                            if (!navManager.getWordHistory().isEmpty()
                                    && navManager.getWordHistory().peek().equals(word)) {
                                navManager.getWordHistory().pop();
                            }
                            showSearchFragment(getString(R.string.error_word_not_found, word));
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

    private Language getLanguageFromPref(SharedPreferences prefs, String key) {
        var iso = prefs.getString(key, null);
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        return Language.ofIsoCode(iso);
    }

    public void showSnackbar(String message, boolean isError) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
