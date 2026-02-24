package com.github.lauroschuck.ankiquickadd;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProcessTextActivity extends AppCompatActivity {

    private static final String TAG = "ProcessTextActivity";
    private WebView webView;
    private TextView statusText;
    private Button createCardsButton;
    private String currentWord = "";

    /**
     * JS Interface to receive extracted data from the WebView.
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void processSelectedCards(String json) {
            runOnUiThread(() -> {
                Log.d(TAG, "Selected cards JSON: " + json);
                List<SwedishCard> cards = SwedishCard.fromJson(json);
                Log.d(TAG, "Selected cards: " + cards);
                if (AnkiDroidHelper.isApiAvailable(ProcessTextActivity.this)) {
                    // Use AnkiDroidActionProvider to handle the click event if the provider is installed
                    AnkiIntegration.createAnkiCards(ProcessTextActivity.this, cards);
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

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // Debugging: Forward JS console.log to Android Logcat (Filter: WebViewConsole)
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
                    fetchWiktionaryHtml(word);
                    return true;
                }
                return true;
            }
        });
    }

    private void handleIntent(Intent intent) {
        String selectedWord = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        if (selectedWord != null) {
            fetchWiktionaryHtml(selectedWord.toLowerCase(Locale.ROOT));
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Select a word to start");
        }
    }

    private void fetchWiktionaryHtml(String word) {
        currentWord = word;
        runOnUiThread(() -> {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Fetching '" + word + "'...");
            createCardsButton.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
        });

        String url = "https://en.wiktionary.org/w/api.php?action=parse&prop=text&format=json&redirects=1&page=" + Uri.encode(word);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).header("User-Agent", "AnkiDroidQuickAdd/1.0").build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processHtmlResponse(response.body().string());
            }
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> statusText.setText("Error: " + e.getMessage()));
            }
        });
    }

    private void processHtmlResponse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("error")) {
                runOnUiThread(() -> statusText.setText("Word not found."));
                return;
            }

            String rawHtml = obj.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
            Document doc = Jsoup.parse(rawHtml);
            Element root = doc.selectFirst(".mw-parser-output");
            if (root == null) root = doc.body();

            // 1. Identify Swedish section
            Element swedishHeader = findSwedishHeader(root);
            if (swedishHeader == null) {
                runOnUiThread(() -> statusText.setText("No Swedish section found."));
                return;
            }

            // 2. Pre-process: remove unwanted elements
            cleanUnwantedElements(root);

            // 3. Extract only the Swedish content
            String swedishHtml = extractSwedishContent(swedishHeader);

            // 4. Final transformations
            Document swDoc = Jsoup.parseBodyFragment(swedishHtml);
            transformLinks(swDoc);
            injectCheckboxes(swDoc);

            String finalHtml = buildHtmlPage(swDoc.body().html());

            runOnUiThread(() -> {
                statusText.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadDataWithBaseURL("https://en.wiktionary.org/", finalHtml, "text/html", "UTF-8", null);
                createCardsButton.setVisibility(View.VISIBLE);
            });

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            runOnUiThread(() -> statusText.setText("Error: " + e.getMessage()));
        }
    }

    private Element findSwedishHeader(Element root) {
        Element header = root.selectFirst(".mw-heading2:has(#Swedish), h2:has(#Swedish)");
        if (header == null) {
            Element swId = root.selectFirst("#Swedish");
            if (swId != null) header = swId.closest(".mw-heading2, h2");
        }
        return header;
    }

    private void cleanUnwantedElements(Element root) {
        // interproject-box: links to Wikipedia
        // figure: images on some definitions (bok)
        // mw-empty-elt: present on some definition lists (ben)
        root.select(".mw-editsection, .interproject-box, figure, .mw-empty-elt").remove();

        // Remove entire sections that are noisy for flashcards
        String[] ids = {"Anagrams", "Further_reading", "Quotations"};
        for (String id : ids) {
            Element h = root.selectFirst(".mw-heading:has(#" + id + "), h2:has(#" + id + "), h3:has(#" + id + "), h4:has(#" + id + ")");
            if (h != null) {
                Element next = h.nextElementSibling();
                while (next != null && !next.tagName().matches("h[2-6]") && !next.hasClass("mw-heading")) {
                    Element toRemove = next;
                    next = next.nextElementSibling();
                    toRemove.remove();
                }
                h.remove();
            }
        }
    }

    private String extractSwedishContent(Element header) {
        StringBuilder sb = new StringBuilder(header.outerHtml());
        Element next = header.nextElementSibling();
        while (next != null && !next.hasClass("mw-heading2") && !next.tagName().equalsIgnoreCase("h2")) {
            sb.append(next.outerHtml());
            next = next.nextElementSibling();
        }
        return sb.toString();
    }

    private void transformLinks(Document doc) {
        for (Element a : doc.select("a")) {
            String href = a.attr("href");
            if (href.contains("/wiki/") && href.contains("#Swedish")) {
                int start = href.indexOf("/wiki/") + 6;
                int end = href.indexOf("#Swedish");
                if (end > start) {
                    a.attr("href", "app://fetch/" + href.substring(start, end));
                    continue;
                }
            }
            a.unwrap();
        }
    }

    private void injectCheckboxes(Document doc) {
        for (Element ex : doc.select(".h-usage-example, .ux-example")) {
            ex.prepend("<input type='checkbox' class='example-checkbox'> ");
        }
    }

    private String buildHtmlPage(String bodyContent) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; }
                h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; }
                h4 { font-size: 1.15em; font-weight: bold; margin-top: 1.0em; }
                h5 { font-size: 1.05em; font-weight: bold; margin-top: 0.8em; }
                ol { padding-left: 1.5em; }
                li { margin-bottom: 0.5em; }
                .h-usage-example, .ux-example { font-style: italic; display: block; margin-top: 0.5em; }
                .h-usage-example-translation, .mention-gloss { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; }
                a { color: #36c; text-decoration: none; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 0.85em; }
                table th, table td { border: 1px solid #a2a9b1; padding: 6px; text-align: center; }
                table th { background-color: #f8f9fa; }
                .example-checkbox { margin-right: 8px; vertical-align: middle; }""";

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body data-word='" + currentWord + "'>" + bodyContent + "</body></html>";
    }

    private void triggerJsExtraction() {
        String js = """
                (() => {
                    const cards = [];
                    const rootWord = document.body.getAttribute('data-word');
                    console.log('Starting card extraction for word: ' + rootWord);
                    
                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const container = cb.parentElement;
                        
                        // 1. Extract Swedish HTML (preserve bolding)
                        const swedishEl = container.querySelector('.e-example, [lang=sv], .ux-example');
                        let swedish = null;
                        
                        if (swedishEl) {
                            // Create a clone to manipulate so we don't break the UI
                            const tempDiv = document.createElement('div');
                            tempDiv.innerHTML = swedishEl.innerHTML;
                            
                            // Remove all links (<a> tags) but keep their text content
                            tempDiv.querySelectorAll('a').forEach(a => {
                                const textNode = document.createTextNode(a.textContent);
                                a.parentNode.replaceChild(textNode, a);
                            });
                            
                            swedish = tempDiv.innerHTML.trim();
                        }
                        
                        // 2. Extract English HTML
                        let englishEl = container.querySelector('.e-translation, .h-usage-example-translation, .ux-translation, .mention-gloss, .translation');
                        // Fallback: check if it is a sibling instead of a child
                        if (!englishEl && container.nextElementSibling && container.nextElementSibling.matches('.e-translation, .h-usage-example-translation, .ux-translation, .mention-gloss, .translation')) {
                            englishEl = container.nextElementSibling;
                        }
                        const english = englishEl ? englishEl.innerHTML.trim() : null;
                        
                        // 3. Extract the Word Meaning (the parent definition <li>)
                        const li = container.closest('li');
                        let definition = null;
                        if (li) {
                            const clone = li.cloneNode(true);
                            clone.querySelectorAll('dl, ul, ol').forEach(el => el.remove());
                            definition = clone.innerText.trim();
                        }
                        
                        // 4. Extract Grammatical Class (search upwards for the nearest H3/H4/H5)
                        let grammaticalClass = 'Unknown';
                        let searchNode = li || container;
                        while (searchNode && grammaticalClass === 'Unknown') {
                            let ol = searchNode.closest('ol');
                            if (!ol) break;
                            
                            let curr = ol;
                            while (curr) {
                                curr = curr.previousElementSibling;
                                if (!curr) break;
                                // Look for header directly or within a mw-heading wrapper
                                const h = curr.matches('h3, h4, h5') ? curr : curr.querySelector('h3, h4, h5');
                                if (h) {
                                    grammaticalClass = h.innerText.replace('[edit]', '').trim();
                                    break;
                                }
                                if (curr.tagName === 'H2') break;
                            }
                            if (grammaticalClass !== 'Unknown') break;
                            searchNode = ol.parentElement; // Move up to parent OL level
                        }
                        
                        cards.push({ word: rootWord, swedish, english, definition, grammaticalClass });
                    });
                    
                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
        webView.evaluateJavascript(js, null);
    }
}
