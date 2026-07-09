package com.github.lauroschuck.quickcard4ankidroid.ui.settings;

import android.content.SharedPreferences;
import android.icu.text.CompactDecimalFormat;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.data.DatabaseRemoteStorage;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import timber.log.Timber;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_LEARNING_LANGUAGE = "learning_language";
    public static final String KEY_NATIVE_LANGUAGE = "native_language";
    public static final String KEY_PARENT_DECK_NAME = "parent_deck_name";
    public static final String KEY_DECK_NAME = "deck_name";
    public static final String KEY_USE_DEFAULT_DECK_NAME = "use_default_deck_name";
    public static final String KEY_THEME = "ui_theme";
    public static final String DEFAULT_PARENT_DECK_NAME = "QuickCard for AnkiDroid";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private EditText parentDeckNameEditText;
    private EditText deckNameEditText;
    private CheckBox useDefaultDeckNameCheckbox;
    private RecyclerView dictionariesRecyclerView;
    private Button clearCacheButton;
    private Button addDictionaryButton;
    private Spinner themeSpinner;
    private SwitchCompat usageDataSwitch;

    private SharedPreferences prefs;
    private MainViewModel viewModel;
    private DictionaryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        initViews();

        View topBarContainer = findViewById(R.id.settingsTopBarContainer);
        View root = findViewById(android.R.id.content);

        ViewCompat.setOnApplyWindowInsetsListener(topBarContainer, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        setupRecyclerView();
        setupObservers();

        // Load initial data
        parentDeckNameEditText.setText(prefs.getString(KEY_PARENT_DECK_NAME, DEFAULT_PARENT_DECK_NAME));
        useDefaultDeckNameCheckbox.setChecked(prefs.getBoolean(KEY_USE_DEFAULT_DECK_NAME, true));
        deckNameEditText.setText(prefs.getString(KEY_DECK_NAME, ""));
        deckNameEditText.setEnabled(!useDefaultDeckNameCheckbox.isChecked());

        if (useDefaultDeckNameCheckbox.isChecked()) {
            updateDefaultDeckName();
        }
    }

    private void initViews() {
        parentDeckNameEditText = findViewById(R.id.parentDeckNameEditText);
        deckNameEditText = findViewById(R.id.deckNameEditText);
        useDefaultDeckNameCheckbox = findViewById(R.id.useDefaultDeckNameCheckbox);
        dictionariesRecyclerView = findViewById(R.id.dictionariesRecyclerView);
        themeSpinner = findViewById(R.id.themeSpinner);
        usageDataSwitch = findViewById(R.id.usageDataSwitch);

        setupThemeSpinner();

        usageDataSwitch.setChecked(prefs.getBoolean(FirebaseHelper.KEY_FIREBASE_CONSENT, false));
        usageDataSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit()
                    .putBoolean(FirebaseHelper.KEY_FIREBASE_CONSENT, isChecked)
                    .apply();
            FirebaseHelper.updateFirebaseCollectionState(isChecked);
        });

        useDefaultDeckNameCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            deckNameEditText.setEnabled(!isChecked);
            if (isChecked) {
                updateDefaultDeckName();
            }
        });

        clearCacheButton = findViewById(R.id.clearCacheButton);
        addDictionaryButton = findViewById(R.id.addDictionaryButton);

        addDictionaryButton.setOnClickListener(v -> showDownloadDialog());

        clearCacheButton.setOnClickListener(v -> confirmClearCache());
    }

    private void setupThemeSpinner() {
        String[] themes = {
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(adapter);

        int currentTheme = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        themeSpinner.setSelection(currentTheme);

        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int savedTheme = prefs.getInt(KEY_THEME, THEME_SYSTEM);
                if (savedTheme != position) {
                    prefs.edit().putInt(KEY_THEME, position).apply();
                    applyTheme(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyTheme(int themeMode) {
        switch (themeMode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void setupRecyclerView() {
        adapter = new DictionaryAdapter(new DictionaryAdapter.OnDictionaryActionListener() {
            @Override
            public void onSelect(Language learning, Language nativeLang) {
                setActiveDictionary(learning, learning, nativeLang);
            }

            @Override
            public void onDelete(MainViewModel.DownloadedDictionary dict) {
                confirmDelete(dict);
            }

            @Override
            public void onUpdate(MainViewModel.DownloadedDictionary dict) {
                confirmUpdate(dict);
            }

            @Override
            public void onInfo(MainViewModel.DownloadedDictionary dict) {
                showDictionaryInfo(dict);
            }

            @Override
            public void onLegacy(MainViewModel.DownloadedDictionary dict) {
                showLegacyInfo();
            }
        });
        dictionariesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        dictionariesRecyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getDownloadedDictionaries().observe(this, dicts -> {
            updateAdapterData();
        });

        viewModel.getActiveDownload().observe(this, new Observer<>() {
            private boolean isDownloading = false;

            @Override
            public void onChanged(MainViewModel.DownloadInfo info) {
                updateAdapterData();
                addDictionaryButton.setVisibility(info == null ? View.VISIBLE : View.GONE);

                if (info != null && !isDownloading) {
                    // Only scroll to top when the download actually starts
                    dictionariesRecyclerView.scrollToPosition(0);
                    isDownloading = true;
                } else if (info == null) {
                    isDownloading = false;
                }
            }
        });

        viewModel.getDownloadError().observe(this, errorMessage -> {
            if (errorMessage != null) {
                var rootView = findViewById(android.R.id.content);
                if (rootView != null) {
                    var snackbar = com.google.android.material.snackbar.Snackbar.make(
                            rootView, errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
                    snackbar.setBackgroundTint(androidx.core.content.ContextCompat.getColor(this, R.color.error_red));
                    snackbar.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
                    snackbar.show();
                } else {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                }
                viewModel.clearDownloadError();
            }
        });
    }

    private void updateDefaultDeckName() {
        if (!useDefaultDeckNameCheckbox.isChecked()) {
            return;
        }

        String lIso = prefs.getString(KEY_LEARNING_LANGUAGE, "");
        String nIso = prefs.getString(KEY_NATIVE_LANGUAGE, "");

        if (!lIso.isEmpty() && !nIso.isEmpty()) {
            Language learning = Language.ofIsoCode(lIso);
            Language nativeLang = Language.ofIsoCode(nIso);
            String defaultName =
                    String.format("%s-%s", learning.getDisplayName(nativeLang), nativeLang.getDisplayName(nativeLang));
            deckNameEditText.setText(defaultName);
        } else {
            deckNameEditText.setText("");
        }
    }

    private void updateAdapterData() {
        String lIso = prefs.getString(KEY_LEARNING_LANGUAGE, "");
        String nIso = prefs.getString(KEY_NATIVE_LANGUAGE, "");
        updateDefaultDeckName();
        List<MainViewModel.DownloadedDictionary> dicts =
                viewModel.getDownloadedDictionaries().getValue();
        MainViewModel.DownloadInfo info = viewModel.getActiveDownload().getValue();
        if (dicts != null) {
            adapter.setData(dicts, lIso, nIso, info);
        }
    }

    private void showDownloadDialog() {
        var view = getLayoutInflater().inflate(R.layout.dialog_download_dictionary, null);
        Spinner learningSpinner = view.findViewById(R.id.newLearningLanguageSpinner);
        Spinner nativeSpinner = view.findViewById(R.id.newNativeLanguageSpinner);
        TextView statsText = view.findViewById(R.id.dictionaryStatsText);

        List<Language> learningLanguages = new ArrayList<>(viewModel.getAvailableLearningLanguages());
        learningLanguages.sort(Comparator.comparing(Language::getDisplayName));
        ArrayAdapter<Language> learningAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, learningLanguages);
        learningAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        learningSpinner.setAdapter(learningAdapter);

        learningSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Language selected = (Language) parent.getItemAtPosition(position);
                List<Language> nativeLanguages = new ArrayList<>(viewModel.getAvailableNativeLanguages(selected));
                nativeLanguages.sort(Comparator.comparing(Language::getDisplayName));
                ArrayAdapter<Language> nativeAdapter = new ArrayAdapter<>(
                        SettingsActivity.this, android.R.layout.simple_spinner_item, nativeLanguages);
                nativeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                nativeSpinner.setAdapter(nativeAdapter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        nativeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Language learning = (Language) learningSpinner.getSelectedItem();
                Language nativeLang = (Language) nativeSpinner.getSelectedItem();
                DatabaseRemoteStorage.DictionaryStats stats = viewModel.getStatsFor(learning, nativeLang);
                if (stats != null) {
                    statsText.setVisibility(View.VISIBLE);
                    String sizeStr = Formatter.formatShortFileSize(SettingsActivity.this, stats.sizeBytes());
                    CompactDecimalFormat df =
                            CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT);
                    statsText.setText(getString(
                            R.string.settings_dictionary_stats_format,
                            sizeStr,
                            formatInstant(stats.lastModified()),
                            df.format(stats.headwords()),
                            df.format(stats.glosses()),
                            df.format(stats.examples()),
                            df.format(stats.pronunciations())));
                } else {
                    statsText.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton(R.string.settings_download_button, (dialog, which) -> {
                    Language learning = (Language) learningSpinner.getSelectedItem();
                    Language nativeLang = (Language) nativeSpinner.getSelectedItem();
                    if (learning != null && nativeLang != null) {
                        viewModel.downloadDictionary(learning, nativeLang);
                    }
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private static String formatInstant(Instant lastMod) {
        return formatInstant(lastMod, "Unknown");
    }

    private static String formatInstant(Instant lastMod, String fallback) {
        if (lastMod != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withZone(ZoneId.systemDefault())
                    .withLocale(Locale.US);
            return formatter.format(lastMod);
        } else {
            return fallback;
        }
    }

    private void showDictionaryInfo(MainViewModel.DownloadedDictionary dict) {
        DatabaseRemoteStorage.DictionaryStats stats = dict.localStats();
        String sizeStr = Formatter.formatShortFileSize(this, stats.sizeBytes());
        CompactDecimalFormat df = CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT);
        String message = getString(
                R.string.settings_dictionary_stats_format,
                sizeStr,
                formatInstant(stats.lastModified()),
                df.format(stats.headwords()),
                df.format(stats.glosses()),
                df.format(stats.examples()),
                df.format(stats.pronunciations()));

        String title = getString(
                R.string.settings_dictionary_info_title_format,
                dict.learning().getDisplayName(),
                dict.nativeLang().getDisplayName());

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }

    private void showLegacyInfo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_legacy_dict_title)
                .setMessage(R.string.settings_legacy_dict_message)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }

    private void setActiveDictionary(Language learningContext, Language learning, Language nativeLang) {
        prefs.edit()
                .putString(KEY_LEARNING_LANGUAGE, learning.getIsoCode())
                .putString(KEY_NATIVE_LANGUAGE, nativeLang.getIsoCode())
                .apply();
        updateDefaultDeckName();
        updateAdapterData();
        FirebaseHelper.setUserLanguages(learning, nativeLang);
        Toast.makeText(
                        this,
                        getString(
                                R.string.settings_active_dict_changed,
                                learning.getDisplayName(),
                                nativeLang.getDisplayName()),
                        Toast.LENGTH_SHORT)
                .show();
    }

    private void confirmDelete(MainViewModel.DownloadedDictionary dict) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_delete_dict_title)
                .setMessage(getString(
                        R.string.settings_delete_dict_message,
                        dict.learning().getDisplayName(),
                        dict.nativeLang().getDisplayName()))
                .setPositiveButton(R.string.dict_delete_description, (dialog, which) -> {
                    viewModel.deleteDictionary(dict);
                    FirebaseHelper.logDeleteDictionary(dict.learning(), dict.nativeLang());
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void confirmUpdate(MainViewModel.DownloadedDictionary dict) {
        DatabaseRemoteStorage.DictionaryStats remote = viewModel.getStatsFor(dict.learning(), dict.nativeLang());
        if (remote == null) {
            return;
        }

        DatabaseRemoteStorage.DictionaryStats local = dict.localStats();

        long sizeDiff = remote.sizeBytes() - local.sizeBytes();
        String comparison = getString(
                        R.string.settings_update_comparison_line,
                        getString(R.string.settings_stats_headwords),
                        local.headwords(),
                        remote.headwords(),
                        (remote.headwords() - local.headwords()))
                + "\n"
                + getString(
                        R.string.settings_update_comparison_line,
                        getString(R.string.settings_stats_definitions),
                        local.glosses(),
                        remote.glosses(),
                        (remote.glosses() - local.glosses()))
                + "\n"
                + getString(
                        R.string.settings_update_comparison_line,
                        getString(R.string.settings_stats_examples),
                        local.examples(),
                        remote.examples(),
                        (remote.examples() - local.examples()))
                + "\n"
                + getString(
                        R.string.settings_update_comparison_line,
                        getString(R.string.settings_stats_pronunciations),
                        local.pronunciations(),
                        remote.pronunciations(),
                        (remote.pronunciations() - local.pronunciations()))
                + "\n"
                + getString(
                        R.string.settings_update_comparison_line_size,
                        getString(R.string.settings_stats_size),
                        Formatter.formatShortFileSize(this, local.sizeBytes()),
                        Formatter.formatShortFileSize(this, remote.sizeBytes()),
                        (Math.signum(sizeDiff) >= 0 ? "+" : "-")
                                + Formatter.formatShortFileSize(
                                        this, Math.abs(remote.sizeBytes() - local.sizeBytes())));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_update_dict_title)
                .setMessage(getString(
                        R.string.settings_update_dict_message,
                        dict.learning().getDisplayName(),
                        dict.nativeLang().getDisplayName(),
                        formatInstant(remote.lastModified()),
                        comparison))
                .setPositiveButton(R.string.settings_update_confirm, (dialog, which) -> {
                    viewModel.updateDictionary(dict);
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void confirmClearCache() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_cache_title)
                .setMessage(R.string.settings_clear_cache_message)
                .setPositiveButton(R.string.common_close, (dialog, which) -> clearAppCache())
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void clearAppCache() {
        try {
            // 1. Clear WebView cache
            new WebView(this).clearCache(true);

            // 2. Clear internal cache directory
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDir(cacheDir);
            }

            // 3. Clear temporary files in databases directory
            File dbDir = getDatabasePath("unused").getParentFile();
            if (dbDir != null && dbDir.exists()) {
                File[] files = dbDir.listFiles((dir, name) -> name.endsWith(".tmp"));
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }

            Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            Timber.e(e, "Failed to clear app cache");
            Toast.makeText(this, R.string.settings_cache_clear_failed, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        String parentDeckName = parentDeckNameEditText.getText().toString().trim();
        if (parentDeckName.isEmpty()) {
            parentDeckName = DEFAULT_PARENT_DECK_NAME;
        }

        String deckName = deckNameEditText.getText().toString().trim();
        boolean useDefault = useDefaultDeckNameCheckbox.isChecked();

        // If custom name is empty, we must revert to default or prevent saving empty
        if (!useDefault && deckName.isEmpty()) {
            // Re-trigger default update if custom was left empty
            useDefault = true;
            updateDefaultDeckName();
            deckName = deckNameEditText.getText().toString().trim();
        }

        prefs.edit()
                .putString(KEY_PARENT_DECK_NAME, parentDeckName)
                .putString(KEY_DECK_NAME, deckName)
                .putBoolean(KEY_USE_DEFAULT_DECK_NAME, useDefault)
                .apply();
    }
}
