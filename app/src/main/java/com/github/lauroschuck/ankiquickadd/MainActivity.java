package com.github.lauroschuck.ankiquickadd;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
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
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AnkiQuickAdd";
    private WebView webView;
    private TextView statusText;
    private Button createCardsButton;
    private TabLayout tabLayout;
    private String currentWord = "";
    
    private final List<DictionarySource> sources = new ArrayList<>();
    private DictionarySource currentSource;

    /**
     * JS Interface to receive extracted data from the WebView.
     */
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.statusText);
        createCardsButton = findViewById(R.id.createCardsButton);
        tabLayout = findViewById(R.id.tabLayout);

        setupSources();
        configureWebView();
        
        createCardsButton.setOnClickListener(v -> triggerJsExtraction());
        
        handleIntent(getIntent());
    }

    private void setupSources() {
        sources.add(new WiktionarySource());
        sources.add(new WordReferenceSource());
        sources.add(new ReversoSource());
        sources.add(new OfflineKaikkiSource(this));

        tabLayout.addTab(tabLayout.newTab().setText("Wiktionary"));
        tabLayout.addTab(tabLayout.newTab().setText("WordReference"));
        tabLayout.addTab(tabLayout.newTab().setText("Reverso"));
        tabLayout.addTab(tabLayout.newTab().setText("Kaikki"));

        currentSource = sources.get(0);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentSource = sources.get(tab.getPosition());
                if (!currentWord.isEmpty()) {
                    fetchDefinition(currentWord);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        });
    }

    private void handleIntent(Intent intent) {
        String selectedWord = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        if (selectedWord != null) {
            fetchDefinition(selectedWord.toLowerCase(java.util.Locale.ROOT));
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Select a word to start");
        }
    }

    private void fetchDefinition(String word) {
        currentWord = word;
        runOnUiThread(() -> {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Fetching '" + word + "'...");
            createCardsButton.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Language sourceLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_SOURCE_LANGUAGE, Language.SWEDISH);
        Language targetLanguage = getLanguageFromPref(prefs, SettingsActivity.KEY_TARGET_LANGUAGE, Language.ENGLISH);

        currentSource.fetch(word, sourceLanguage, targetLanguage, new DictionarySource.OnResultListener() {
            @Override
            public void onSuccess(String html, String headword) {
                runOnUiThread(() -> {
                    statusText.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    webView.loadDataWithBaseURL("https://en.wiktionary.org/", html, "text/html", "UTF-8", null);
                    createCardsButton.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> statusText.setText("Error: " + message));
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
        String js = currentSource.getExtractionJs();
        webView.evaluateJavascript(js, null);
    }
}
