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
import com.github.lauroschuck.ankiquickadd.source.WiktionarySource;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AnkiQuickAdd";
    private WebView webView;
    private TextView statusText;
    private Button createCardsButton;
    private String currentWord = "";
    
    // The dictionary source interface
    private DictionarySource dictionarySource = new WiktionarySource();

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
                    // Use AnkiDroidActionProvider to handle the click event if the provider is installed
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

        configureWebView();
        createCardsButton.setOnClickListener(v -> triggerJsExtraction());
        handleIntent(getIntent());
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

        // Forward JS console logs to Android Logcat
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
        String sourceIso = prefs.getString(SettingsActivity.KEY_SOURCE_LANGUAGE, Language.SWEDISH.getIsoCode());
        Language sourceLanguage = null;
        for (Language l : Language.values()) {
            if (l.getIsoCode().equals(sourceIso)) {
                sourceLanguage = l;
                break;
            }
        }
        if (sourceLanguage == null) sourceLanguage = Language.SWEDISH;

        dictionarySource.fetch(word, sourceLanguage, new DictionarySource.OnResultListener() {
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

    private void triggerJsExtraction() {
        String js = """
                (() => {
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');
                    
                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const container = cb.parentElement;
                        
                        // 1. Extract sourceText HTML (preserve bolding)
                        const sourceTextEl = container.querySelector('.e-example, [lang=sv], .ux-example');
                        let sourceText = null;
                        if (sourceTextEl) {
                            // Create a clone to manipulate so we don't break the UI
                            const tempDiv = document.createElement('div');
                            tempDiv.innerHTML = sourceTextEl.innerHTML;
                            // Clean links within the phrase
                            tempDiv.querySelectorAll('a').forEach(a => {
                                a.parentNode.replaceChild(document.createTextNode(a.textContent), a);
                            });
                            sourceText = tempDiv.innerHTML.trim();
                        }
                        
                        // 2. Extract targetText HTML
                        let targetTextEl = container.querySelector('.e-translation, .h-usage-example-translation, .ux-translation, .mention-gloss, .translation');
                        if (!targetTextEl && container.nextElementSibling && container.nextElementSibling.matches('.e-translation, .h-usage-example-translation, .ux-translation, .mention-gloss, .translation')) {
                            targetTextEl = container.nextElementSibling;
                        }
                        const targetText = targetTextEl ? targetTextEl.innerHTML.trim() : null;
                        
                        // 3. Extract the definition (parent <li> text)
                        const li = container.closest('li');
                        let definition = null;
                        if (li) {
                            const clone = li.cloneNode(true);
                            clone.querySelectorAll('dl, ul, ol').forEach(el => el.remove());
                            definition = clone.innerText.trim();
                        }
                        
                        // 4. Extract lexicalCategory (walk up to nearest H3/H4/H5)
                        let lexicalCategory = 'Unknown';
                        let searchNode = li || container;
                        while (searchNode && lexicalCategory === 'Unknown') {
                            let ol = searchNode.closest('ol');
                            if (!ol) break;
                            let curr = ol;
                            while (curr) {
                                curr = curr.previousElementSibling;
                                if (!curr) break;
                                // Look for header directly or within a mw-heading wrapper
                                const h = curr.matches('h3, h4, h5') ? curr : curr.querySelector('h3, h4, h5');
                                if (h) {
                                    lexicalCategory = h.innerText.replace('[edit]', '').trim();
                                    break;
                                }
                                if (curr.tagName === 'H2') break;
                            }
                            if (lexicalCategory !== 'Unknown') break;
                            searchNode = ol.parentElement;
                        }
                        
                        cards.push({ headword, sourceText, targetText, definition, lexicalCategory });
                    });
                    
                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
        webView.evaluateJavascript(js, null);
    }
}
