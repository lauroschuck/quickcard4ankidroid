package com.github.lauroschuck.ankiquickadd;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

import androidx.appcompat.app.AppCompatActivity;

import com.github.lauroschuck.ankiquickadd.anki.AnkiDroidHelper;
import com.github.lauroschuck.ankiquickadd.anki.AnkiIntegration;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.model.TranslationCard;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import com.github.lauroschuck.ankiquickadd.source.OfflineKaikkiSource;
import com.github.lauroschuck.ankiquickadd.source.ReversoSource;
import com.github.lauroschuck.ankiquickadd.source.WiktionarySource;
import com.github.lauroschuck.ankiquickadd.source.WordReferenceSource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private String currentWord = "";
    
    private final List<DictionarySource> sources = new ArrayList<>();
    private DictionarySource currentSource;

    public class WebAppInterface {
        @JavascriptInterface
        public void processSelectedCards(String json) {
            runOnUiThread(() -> {
                Log.d(TAG, "Selected cards JSON: " + json);
                List<TranslationCard> cards = TranslationCard.fromJson(json);
                if (AnkiDroidHelper.isApiAvailable(MainActivity.this)) {
                    AnkiIntegration.createAnkiCards(MainActivity.this, cards);
                }
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

        setupSources();
        configureWebView();
        setupSearch();
        
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        createCardsFab.setOnClickListener(v -> triggerJsExtraction());
        closeButton.setOnClickListener(v -> showCentralSearch("Enter a word or select text in another app"));
        
        handleIntent(getIntent());
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

        String[] sourceNames = {"Offline", "Wiktionary", "WordReference", "Reverso"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sourceNames);
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
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject selection change listener
                injectCheckboxListener();
            }
        });
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
            createCardsFabContainer.setVisibility(View.GONE);
            closeButton.setVisibility(View.GONE);
            badgeText.setVisibility(View.GONE); // Reset badge
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Language sourceLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_SOURCE_LANGUAGE, Language.SWEDISH);
        Language targetLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_TARGET_LANGUAGE, Language.ENGLISH);

        currentSource.fetch(word, sourceLanguage, targetLanguage, new DictionarySource.OnResultListener() {
            @Override
            public void onSuccess(String html, String headword) {
                runOnUiThread(() -> {
                    webView.setVisibility(View.VISIBLE);
                    webView.loadDataWithBaseURL("https://en.wiktionary.org/", html, "text/html", "UTF-8", null);
                    // Container remains GONE until a checkbox is selected
                    closeButton.setVisibility(View.VISIBLE);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showCentralSearch("Error: " + message);
                });
            }
        });
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
}
