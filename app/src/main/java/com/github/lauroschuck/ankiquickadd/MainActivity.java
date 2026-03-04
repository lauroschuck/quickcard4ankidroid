package com.github.lauroschuck.ankiquickadd;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.lauroschuck.ankiquickadd.anki.AnkiDroidHelper;
import com.github.lauroschuck.ankiquickadd.anki.AnkiIntegration;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import com.github.lauroschuck.ankiquickadd.source.OfflineKaikkiSource;
import com.github.lauroschuck.ankiquickadd.source.ReversoSource;
import com.github.lauroschuck.ankiquickadd.source.WiktionarySource;
import com.github.lauroschuck.ankiquickadd.source.WordReferenceSource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AnkiQuickAdd";
    private WebView webView;
    private View centralContainer;
    private TextView statusText;
    private View createCardsFabContainer;
    private FloatingActionButton createCardsFab;
    private TextView badgeText;
    private FloatingActionButton closeButton;
    private Spinner sourceSpinner;
    private ImageButton settingsButton;
    private EditText searchEditText;
    private Button searchButton;
    private View noteTypeHeader;
    private TabLayout noteTypeTabLayout;
    private String currentWord = "";
    private MediaPlayer mediaPlayer;
    
    private final List<DictionarySource> sources = new ArrayList<>();
    private DictionarySource currentSource;

    public class WebAppInterface {
        @JavascriptInterface
        public void processSelectedCards(String json) {
            runOnUiThread(() -> {
                Log.d(TAG, "Selected cards JSON: " + json);
                currentSource.getCardsFromSelection(json, cards -> {
                    runOnUiThread(() -> {
                        if (AnkiDroidHelper.isApiAvailable(MainActivity.this)) {
                            boolean isDefinitions = noteTypeTabLayout.getSelectedTabPosition() == 0;
                            AnkiIntegration.createAnkiCards(MainActivity.this, cards, isDefinitions);
                        }
                    });
                });
            });
        }

        @JavascriptInterface
        public void updateSelectedCount(int count) {
            runOnUiThread(() -> {
                if (count > 0) {
                    createCardsFabContainer.setVisibility(View.VISIBLE);
                    badgeText.setVisibility(View.VISIBLE);
                    badgeText.setText(String.valueOf(count));
                } else {
                    createCardsFabContainer.setVisibility(View.GONE);
                    badgeText.setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        centralContainer = findViewById(R.id.centralContainer);
        statusText = findViewById(R.id.statusText);
        createCardsFabContainer = findViewById(R.id.createCardsFabContainer);
        createCardsFab = findViewById(R.id.createCardsFab);
        badgeText = findViewById(R.id.badgeText);
        closeButton = findViewById(R.id.closeButton);
        sourceSpinner = findViewById(R.id.sourceSpinner);
        settingsButton = findViewById(R.id.settingsButton);
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        noteTypeHeader = findViewById(R.id.noteTypeHeader);
        noteTypeTabLayout = findViewById(R.id.noteTypeTabLayout);

        setupSources();
        configureWebView();
        setupSearch();
        setupTabs();
        
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        createCardsFab.setOnClickListener(v -> triggerJsExtraction());
        closeButton.setOnClickListener(v -> showCentralSearch("Enter a word or select text in another app"));
        
        handleIntent(getIntent());
    }

    private void setupTabs() {
        // "Dictionary Definitions" is at index 0, "Examples" is at index 1
        noteTypeTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!currentWord.isEmpty()) {
                    webView.setVisibility(View.VISIBLE);
                    String mode = tab.getPosition() == 0 ? "definitions" : "examples";
                    webView.evaluateJavascript("setMode('" + mode + "')", null);
                    // Reset selected count when switching tabs since we haven't implemented cross-tab selection
                    createCardsFabContainer.setVisibility(View.GONE);
                    badgeText.setVisibility(View.GONE);
                } else {
                    webView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Select "Examples" by default for now
        TabLayout.Tab examplesTab = noteTypeTabLayout.getTabAt(1);
        if (examplesTab != null) examplesTab.select();
    }

    private void setupSearch() {
        searchButton.setOnClickListener(v -> performSearch());
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (!query.isEmpty()) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
            fetchDefinition(query);
        }
    }

    private void setupSources() {
        sources.add(new OfflineKaikkiSource(this));
        sources.add(new WiktionarySource());
        sources.add(new WordReferenceSource());
        sources.add(new ReversoSource());

        String[] sourceNames = {"Wiktionary", "Wiktionary (live)", "WordReference", "Reverso"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, sourceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter);

        currentSource = sources.get(0);

        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSource = sources.get(position);
                if (!currentWord.isEmpty()) {
                    fetchDefinition(currentWord);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebViewConsole", consoleMessage.message());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("app://fetch/")) {
                    String word = Uri.decode(url.substring("app://fetch/".length()));
                    fetchDefinition(word);
                    return true;
                } else if (url.startsWith("app://play/")) {
                    String audioUrl = Uri.decode(url.substring("app://play/".length()));
                    playAudio(audioUrl);
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject selection change listener
                injectCheckboxListener();
                // Set initial mode based on selected tab
                String mode = noteTypeTabLayout.getSelectedTabPosition() == 0 ? "definitions" : "examples";
                webView.evaluateJavascript("setMode('" + mode + "')", null);
            }
        });
    }

    private void playAudio(String url) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                showSnackbar("Audio playback failed", true);
                return true;
            });
        } catch (IOException e) {
            Log.e(TAG, "Audio error", e);
        }
    }

    private void injectCheckboxListener() {
        String js = "document.addEventListener('change', function(e) {" +
                    "  if (e.target.classList.contains('example-checkbox')) {" +
                    "    var count = document.querySelectorAll('input.example-checkbox:checked').length;" +
                    "    Android.updateSelectedCount(count);" +
                    "  }" +
                    "});";
        webView.evaluateJavascript(js, null);
    }

    private void handleIntent(Intent intent) {
        String selectedWord = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        if (selectedWord != null) {
            fetchDefinition(selectedWord.toLowerCase(Locale.ROOT));
        } else {
            showCentralSearch("Enter a word or select text in another app");
        }
    }

    private void showCentralSearch(String message) {
        currentWord = "";
        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            noteTypeHeader.setVisibility(View.GONE);
            createCardsFabContainer.setVisibility(View.GONE);
            closeButton.setVisibility(View.GONE);
            centralContainer.setVisibility(View.VISIBLE);
            statusText.setText(message);
            searchEditText.setText("");
        });
    }

    private void fetchDefinition(String word) {
        currentWord = word;
        searchEditText.setText(word);
        runOnUiThread(() -> {
            centralContainer.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
            noteTypeHeader.setVisibility(View.GONE);
            createCardsFabContainer.setVisibility(View.GONE);
            closeButton.setVisibility(View.GONE);
            badgeText.setVisibility(View.GONE);
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Language learningLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_LEARNING_LANGUAGE, Language.SV);
        Language nativeLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_NATIVE_LANGUAGE, Language.EN);

        currentSource.fetch(word, learningLanguage, nativeLanguage, new DictionarySource.OnResultListener() {
            @Override
            public void onSuccess(String html, String headword) {
                runOnUiThread(() -> {
                    noteTypeHeader.setVisibility(View.VISIBLE);
                    webView.setVisibility(View.VISIBLE);
                    webView.loadDataWithBaseURL("https://en.wiktionary.org/", html, "text/html", "UTF-8", null);
                    closeButton.setVisibility(View.VISIBLE);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showSnackbar("Error: " + message, true);
                    showCentralSearch("Enter a word to start");
                });
            }
        });
    }

    private void showSnackbar(String message, boolean isError) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
            int bgColor = isError ? R.color.error_red : R.color.anki_blue;
            snackbar.setBackgroundTint(ContextCompat.getColor(this, bgColor));
            snackbar.setTextColor(ContextCompat.getColor(this, R.color.white));
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private Language getLanguageFromPref(SharedPreferences prefs, String key, Language defaultLang) {
        String iso = prefs.getString(key, defaultLang.getIsoCode());
        for (Language l : Language.values()) {
            if (l.getIsoCode().equals(iso)) return l;
        }
        return defaultLang;
    }

    private void triggerJsExtraction() {
        webView.evaluateJavascript(currentSource.getExtractionJs(), null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
