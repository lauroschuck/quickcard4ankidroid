package com.github.lauroschuck.ankiquickadd;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.lauroschuck.ankiquickadd.data.PCloudRepository;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    private SharedPreferences prefs;
    private MainViewModel viewModel;
    private DictionaryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        initViews();
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

        findViewById(R.id.addDictionaryButton).setOnClickListener(v -> {
            downloadSection.setVisibility(downloadSection.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

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
                setActiveDictionary(learning, nativeLang);
            }

            @Override
            public void onDelete(MainViewModel.DownloadedDictionary dict) {
                confirmDelete(dict);
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

        PCloudRepository.DictionaryStats stats = viewModel.getStatsFor(learning, nativeLang);
        if (stats != null) {
            dictionaryStatsText.setVisibility(View.VISIBLE);
            dictionaryStatsText.setText(
                    String.format(Locale.US, "%d headwords, %d example phrases", stats.headwords(), stats.examples()));
        } else {
            dictionaryStatsText.setVisibility(View.GONE);
        }
    }

    private void setActiveDictionary(Language learning, Language nativeLang) {
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
