package com.github.lauroschuck.quickcard4ankidroid.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.anki.AnkiIntegration;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.CardAssets;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.DictionaryNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.TextNote;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.source.DataSource;
import com.github.lauroschuck.quickcard4ankidroid.ui.definition.DefinitionFragment;
import com.github.lauroschuck.quickcard4ankidroid.ui.search.SearchFragment;
import com.github.lauroschuck.quickcard4ankidroid.ui.settings.SettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
        EdgeToEdge.enable(this);
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
        var topBarContainer = findViewById(R.id.topBarContainer);

        ViewCompat.setOnApplyWindowInsetsListener(topBarContainer, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        viewModel.getDownloadError().observe(this, errorMessage -> {
            if (errorMessage != null) {
                showSnackbar(errorMessage, true);
                viewModel.clearDownloadError();
            }
        });

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

        navigationView.setItemIconTintList(null);
        setupNavigationIcons(navigationView);
        setupSourceSpinner();
        setupBackPressed();

        viewModel.getDownloadedDictionaries().observe(this, this::updateBadges);

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        viewModel.setLastUsedLearningLanguage(getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE));
        viewModel.setLastUsedNativeLanguage(getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE));

        FirebaseHelper.setUserLanguages(viewModel.getLastUsedLearningLanguage(), viewModel.getLastUsedNativeLanguage());

        updateDictionaryTitle();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void updateBadges(List<MainViewModel.DownloadedDictionary> dicts) {
        boolean hasUpdate =
                dicts != null && dicts.stream().anyMatch(MainViewModel.DownloadedDictionary::updateAvailable);

        // Header badge
        View menuBadge = findViewById(R.id.menuBadge);
        if (menuBadge != null) {
            menuBadge.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        }

        // Drawer badge
        NavigationView navigationView = findViewById(R.id.navigationView);
        if (navigationView != null) {
            var settingsItem = navigationView.getMenu().findItem(R.id.nav_settings);
            if (settingsItem != null) {
                var baseIcon =
                        androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_menu_preferences);
                if (baseIcon != null) {
                    baseIcon = baseIcon.mutate();
                    baseIcon.setTint(getColor(android.R.color.white));
                }

                if (hasUpdate && baseIcon != null) {
                    var badgeDrawable =
                            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_badge_exclamation);
                    if (badgeDrawable != null) {
                        var layerDrawable = new android.graphics.drawable.LayerDrawable(
                                new android.graphics.drawable.Drawable[] {baseIcon, badgeDrawable});
                        // Position the badge at top-right of the icon
                        int badgeSize = (int) (16 * getResources().getDisplayMetrics().density);
                        layerDrawable.setLayerSize(1, badgeSize, badgeSize);
                        layerDrawable.setLayerGravity(1, android.view.Gravity.TOP | android.view.Gravity.END);
                        settingsItem.setIcon(layerDrawable);
                    } else {
                        settingsItem.setIcon(baseIcon);
                    }
                } else if (baseIcon != null) {
                    settingsItem.setIcon(baseIcon);
                }
            }
        }
    }

    private void setupNavigationIcons(NavigationView navigationView) {
        var menu = navigationView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            var item = menu.getItem(i);
            var icon = item.getIcon();
            if (icon != null) {
                icon.mutate().setTint(getColor(android.R.color.white));
            }
        }
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

        boolean changed = !Objects.equals(currentLearning, viewModel.getLastUsedLearningLanguage())
                || !Objects.equals(currentNative, viewModel.getLastUsedNativeLanguage());

        if (changed) {
            if (currentLearning != null && currentNative != null) {
                var word = viewModel.getNavigationManager().getCurrentWord().getValue();
                if (word != null && !word.isEmpty()) {
                    Timber.d("Language changed, refetching word: %s", word);
                    fetchDefinition(word, true);
                }
            }
            viewModel.setLastUsedLearningLanguage(currentLearning);
            viewModel.setLastUsedNativeLanguage(currentNative);
            FirebaseHelper.setUserLanguages(currentLearning, currentNative);
            updateDictionaryTitle();
            setupSourceSpinner();
        }

        viewModel.getWordRepository().reload();
    }

    private void updateDictionaryTitle() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var learningLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE);
        var nativeLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE);

        TextView dictionaryTitle = findViewById(R.id.dictionaryTitle);
        if (learningLanguage != null && nativeLanguage != null) {
            String title = String.format("%s-%s", learningLanguage.getDisplayName(), nativeLanguage.getDisplayName());
            dictionaryTitle.setText(title);
            dictionaryTitle.setVisibility(View.VISIBLE);
        } else {
            dictionaryTitle.setVisibility(View.GONE);
        }
    }

    private void setupSourceSpinner() {
        var availableSources = viewModel.getDataSourceRepository().getSources();

        if (availableSources.size() <= 1) {
            sourceSpinner.setVisibility(View.GONE);
        } else {
            sourceSpinner.setVisibility(View.VISIBLE);
            List<String> sourceNames =
                    availableSources.stream().map(DataSource::getName).collect(Collectors.toList());
            var adapter = new ArrayAdapter<>(this, R.layout.spinner_item, sourceNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceSpinner.setAdapter(adapter);
        }

        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                var repo = viewModel.getDataSourceRepository();
                var selectedSource = repo.getSources().get(position);
                var currentSource = repo.getCurrentSource();

                if (currentSource != null && currentSource.getName().equals(selectedSource.getName())) {
                    return;
                }

                repo.selectSource(position);
                Timber.d(
                        "Source selected: %s",
                        viewModel.getDataSourceRepository().getCurrentSource().getName());
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
        if (warning == null) {
            viewModel.getNavigationManager().setCurrentWord("");
        }
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

        navManager.setIsSearching(true);
        navManager.setCurrentWord(word);
        navManager.setCurrentHtml("");
        navManager.setSearchWarning(null);

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var learningLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE);
        var nativeLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE);

        if (learningLanguage == null || nativeLanguage == null) {
            navManager.setIsSearching(false);
            showSearchFragment(getString(R.string.error_select_dictionary));
            return;
        }

        var currentSource = viewModel.getDataSourceRepository().getCurrentSource();
        if (currentSource != null) {
            currentSource.fetch(word, learningLanguage, nativeLanguage, new DataSource.OnResultListener() {
                @Override
                public void onSuccess(String html, String headword) {
                    Timber.d("Successfully fetched definition for: %s", word);
                    FirebaseHelper.logFetchDefinition(word, true);
                    runOnUiThread(() -> {
                        navManager.setIsSearching(false);
                        if (!isFromHistory) {
                            navManager.pushHistory(word);
                        }
                        navManager.setCurrentHtml(html);

                        // Only now switch to DefinitionFragment if we are not already there
                        if (!(getCurrentFragment() instanceof DefinitionFragment)) {
                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragmentContainer, new DefinitionFragment())
                                    .commitNow();
                        }
                    });
                }

                @Override
                public void onNotFound() {
                    Timber.i("Could not find definition for '%s'", word);
                    var lowercaseWord = word.toLowerCase(Locale.ROOT);
                    if (!word.equals(lowercaseWord)) {
                        Timber.d("Retrying with lowercase word: '%s'", lowercaseWord);
                        fetchDefinition(lowercaseWord, true);
                    } else {
                        FirebaseHelper.logFetchDefinition(word, false);
                        runOnUiThread(() -> {
                            navManager.setIsSearching(false);
                            if (!navManager.getWordHistory().isEmpty()
                                    && navManager.getWordHistory().peek().equals(word)) {
                                navManager.getWordHistory().pop();
                            }
                            showSearchFragment(getString(R.string.error_word_not_found, currentSource.getName(), word));
                        });
                    }
                }

                @Override
                public void onError(String userMessage, Exception exception) {
                    Timber.e(exception, "Serious error fetching definition for '%s': %s", word, userMessage);
                    runOnUiThread(() -> {
                        navManager.setIsSearching(false);
                        showSnackbar(userMessage == null ? "Unknown failure" : userMessage, true);
                    });
                }
            });
        } else {
            navManager.setIsSearching(false);
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
        View snackbarView = null;
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null && currentFragment.getView() != null) {
            snackbarView = currentFragment.getView();
        }

        if (snackbarView == null) {
            snackbarView = findViewById(android.R.id.content);
        }

        if (snackbarView != null) {
            var snackbar = Snackbar.make(snackbarView, message, Snackbar.LENGTH_LONG);
            var bgColor = isError ? R.color.error_red : R.color.anki_blue;
            snackbar.setBackgroundTint(androidx.core.content.ContextCompat.getColor(this, bgColor));
            snackbar.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AnkiIntegration.AD_PERM_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPermissionResultDialog(getString(R.string.permission_granted_message), false);
            } else {
                showPermissionResultDialog(getString(R.string.permission_denied_message), true);
            }
        }
    }

    private void showPermissionResultDialog(String message, boolean isError) {
        var iconRes = isError ? R.drawable.ic_badge_exclamation : R.drawable.ic_check;

        var icon = ContextCompat.getDrawable(this, iconRes);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            if (!isError) {
                DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.anki_blue));
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(message)
                .setIcon(icon)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }
}
