package com.github.lauroschuck.ankiquickadd.ui.settings;

import android.content.SharedPreferences;
import android.icu.text.CompactDecimalFormat;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.lauroschuck.ankiquickadd.R;
import com.github.lauroschuck.ankiquickadd.data.DatabaseRemoteStorage;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.ui.main.MainViewModel;

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
    public static final String DEFAULT_PARENT_DECK_NAME = "Anki Quick Add";

    private EditText parentDeckNameEditText;
    private RecyclerView dictionariesRecyclerView;
    private View downloadSection;
    private Spinner newLearningLanguageSpinner;
    private Spinner newNativeLanguageSpinner;
    private TextView dictionaryStatsText;
    private Button downloadButton;
    private Button clearCacheButton;

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
    }

    private void initViews() {
        parentDeckNameEditText = findViewById(R.id.parentDeckNameEditText);
        dictionariesRecyclerView = findViewById(R.id.dictionariesRecyclerView);
        downloadSection = findViewById(R.id.downloadSection);
        newLearningLanguageSpinner = findViewById(R.id.newLearningLanguageSpinner);
        newNativeLanguageSpinner = findViewById(R.id.newNativeLanguageSpinner);
        dictionaryStatsText = findViewById(R.id.dictionaryStatsText);
        downloadButton = findViewById(R.id.downloadButton);
        clearCacheButton = findViewById(R.id.clearCacheButton);

        findViewById(R.id.addDictionaryButton).setOnClickListener(v -> {
            downloadSection.setVisibility(downloadSection.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        clearCacheButton.setOnClickListener(v -> confirmClearCache());

        downloadButton.setOnClickListener(v -> {
            Language learning = (Language) newLearningLanguageSpinner.getSelectedItem();
            Language nativeLang = (Language) newNativeLanguageSpinner.getSelectedItem();
            if (learning != null && nativeLang != null) {
                viewModel.downloadDictionary(learning, nativeLang);
            }
        });

        newLearningLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Language selected = (Language) parent.getItemAtPosition(position);
                updateNativeSpinner(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        newNativeLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateStatsDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
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
        });
        dictionariesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        dictionariesRecyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getDownloadedDictionaries().observe(this, dicts -> {
            updateAdapterData();
        });

        viewModel.getActiveDownload().observe(this, info -> {
            updateAdapterData();
            downloadButton.setEnabled(info == null);
        });

        viewModel.getObservableStats().observe(this, stats -> {
            updateLearningSpinner();
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

    private void updateAdapterData() {
        String lIso = prefs.getString(KEY_LEARNING_LANGUAGE, "");
        String nIso = prefs.getString(KEY_NATIVE_LANGUAGE, "");
        List<MainViewModel.DownloadedDictionary> dicts =
                viewModel.getDownloadedDictionaries().getValue();
        MainViewModel.DownloadInfo info = viewModel.getActiveDownload().getValue();
        if (dicts != null) {
            adapter.setData(dicts, lIso, nIso, info);
        }
    }

    private void updateLearningSpinner() {
        List<Language> languages = viewModel.getAvailableLearningLanguages();
        List<Language> sortedList = new ArrayList<>(languages);
        sortedList.sort(Comparator.comparing(Language::getDisplayName));

        ArrayAdapter<Language> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortedList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        newLearningLanguageSpinner.setAdapter(adapter);
    }

    private void updateNativeSpinner(Language learning) {
        List<Language> languages = viewModel.getAvailableNativeLanguages(learning);
        List<Language> sortedList = new ArrayList<>(languages);
        sortedList.sort(Comparator.comparing(Language::getDisplayName));

        ArrayAdapter<Language> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortedList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        newNativeLanguageSpinner.setAdapter(adapter);

        updateStatsDisplay();
    }

    private void updateStatsDisplay() {
        Language learning = (Language) newLearningLanguageSpinner.getSelectedItem();
        Language nativeLang = (Language) newNativeLanguageSpinner.getSelectedItem();

        DatabaseRemoteStorage.DictionaryStats stats = viewModel.getStatsFor(learning, nativeLang);
        if (stats != null) {
            dictionaryStatsText.setVisibility(View.VISIBLE);
            String sizeStr = Formatter.formatShortFileSize(this, stats.sizeBytes());

            Instant lastMod = stats.lastModified();
            String formattedDate;
            if (lastMod != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault())
                        .withLocale(Locale.US);
                formattedDate = formatter.format(lastMod);
            } else {
                formattedDate = "Unknown";
            }

            CompactDecimalFormat df =
                    CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT);
            dictionaryStatsText.setText(String.format(
                    Locale.US,
                    "Size: %s\nLast modified: %s\n%s headwords\n%s definitions\n%s example phrases\n%s pronunciations",
                    sizeStr,
                    formattedDate,
                    df.format(stats.headwords()),
                    df.format(stats.glosses()),
                    df.format(stats.examples()),
                    df.format(stats.pronunciations())));
        } else {
            dictionaryStatsText.setVisibility(View.GONE);
        }
    }

    private void setActiveDictionary(Language learningContext, Language learning, Language nativeLang) {
        prefs.edit()
                .putString(KEY_LEARNING_LANGUAGE, learning.getIsoCode())
                .putString(KEY_NATIVE_LANGUAGE, nativeLang.getIsoCode())
                .apply();
        updateAdapterData();
        FirebaseHelper.setUserLanguages(learning, nativeLang);
        Toast.makeText(
                        this,
                        String.format(
                                "Active dictionary set to %s-%s",
                                learning.getDisplayName(), nativeLang.getDisplayName()),
                        Toast.LENGTH_SHORT)
                .show();
    }

    private void confirmDelete(MainViewModel.DownloadedDictionary dict) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Dictionary")
                .setMessage(String.format(
                        "Are you sure you want to delete the %s-%s dictionary?",
                        dict.learning().getDisplayName(), dict.nativeLang().getDisplayName()))
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteDictionary(dict);
                    FirebaseHelper.logDeleteDictionary(dict.learning(), dict.nativeLang());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmUpdate(MainViewModel.DownloadedDictionary dict) {
        DatabaseRemoteStorage.DictionaryStats remote = viewModel.getStatsFor(dict.learning(), dict.nativeLang());
        if (remote == null) {
            return;
        }

        DatabaseRemoteStorage.DictionaryStats local = dict.localStats();

        String message = String.format(
                Locale.US,
                "A newer version of the %s-%s dictionary is available.\n\n"
                        + "Comparison:\n"
                        + "- Headwords: %d → %d (%+d)\n"
                        + "- Definitions: %d → %d (%+d)\n"
                        + "- Examples: %d → %d (%+d)\n"
                        + "- Size: %s → %s\n\n"
                        + "Download and update now?",
                dict.learning().getDisplayName(),
                dict.nativeLang().getDisplayName(),
                local.headwords(),
                remote.headwords(),
                (remote.headwords() - local.headwords()),
                local.glosses(),
                remote.glosses(),
                (remote.glosses() - local.glosses()),
                local.examples(),
                remote.examples(),
                (remote.examples() - local.examples()),
                Formatter.formatShortFileSize(this, local.sizeBytes()),
                Formatter.formatShortFileSize(this, remote.sizeBytes()));

        new AlertDialog.Builder(this)
                .setTitle("Update Dictionary")
                .setMessage(message)
                .setPositiveButton("Update", (dialog, which) -> {
                    viewModel.updateDictionary(dict);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("Clear App Cache")
                .setMessage(
                        "This will clear the WebView cache and temporary download files. Your downloaded dictionaries will NOT be deleted. Proceed?")
                .setPositiveButton("Clear", (dialog, which) -> clearAppCache())
                .setNegativeButton("Cancel", null)
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

            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            Timber.e(e, "Failed to clear app cache");
            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show();
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
        prefs.edit().putString(KEY_PARENT_DECK_NAME, parentDeckName).apply();
    }
}
