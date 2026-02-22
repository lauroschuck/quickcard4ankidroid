package com.example.swedishanki;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
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

    /**
     * JS Interface to receive extracted data from the WebView.
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void processSelectedCards(String json) {
            runOnUiThread(() -> {
                Log.d(TAG, "Selected cards JSON: " + json);
                Toast.makeText(ProcessTextActivity.this, "Cards extracted. Check Logcat.", Toast.LENGTH_LONG).show();
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

        // Configure WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
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

        createCardsButton.setOnClickListener(v -> createCards());
        handleIntent(getIntent());
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

    /**
     * Fetches rendered HTML from Wiktionary API.
     */
    private void fetchWiktionaryHtml(String word) {
        runOnUiThread(() -> {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Fetching '" + word + "'...");
            createCardsButton.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
        });
        
        String url = "https://en.wiktionary.org/w/api.php?action=parse&prop=text&format=json&redirects=1&page=" + Uri.encode(word);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).header("User-Agent", "SwedishAnkiQuickAdd/1.0").build();

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

    /**
     * Parses the JSON response, extracts the Swedish section, and cleans the HTML.
     */
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

            // 1. Target the Swedish section
            Element swedishHeader = findSwedishHeader(root);
            if (swedishHeader == null) {
                runOnUiThread(() -> statusText.setText("No Swedish section found."));
                return;
            }

            // 2. Clean up unwanted elements globally
            cleanUnwantedElements(root);

            // 3. Extract only the content between the Swedish header and the next language (H2)
            String swedishHtml = extractSwedishContent(swedishHeader);

            // 4. Final transformations on the subset of HTML
            Document swDoc = Jsoup.parseBodyFragment(swedishHtml);
            transformLinks(swDoc);
            injectCheckboxes(swDoc);

            String finalHtml = wrapWithCss(swDoc.body().html());

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
            Element id = root.selectFirst("#Swedish");
            if (id != null) header = id.closest(".mw-heading2, h2");
        }
        return header;
    }

    private void cleanUnwantedElements(Element root) {
        root.select(".mw-editsection, .interproject-box, figure").remove();
        
        // Remove entire sections that are noisy for flashcards
        String[] sectionsToRemove = {"Anagrams", "Further_reading", "Quotations"};
        for (String id : sectionsToRemove) {
            Element h = root.selectFirst(".mw-heading:has(#" + id + "), h2:has(#" + id + "), h3:has(#" + id + "), h4:has(#" + id + ")");
            if (h != null) removeSection(h);
        }
    }

    private void removeSection(Element header) {
        Element next = header.nextElementSibling();
        while (next != null && !next.tagName().matches("h[2-6]") && !next.hasClass("mw-heading")) {
            Element toRemove = next;
            next = next.nextElementSibling();
            toRemove.remove();
        }
        header.remove();
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

    private String wrapWithCss(String body) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; }
                h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; }
                h4 { font-size: 1.15em; font-weight: bold; margin-top: 1.0em; }
                h5 { font-size: 1.05em; font-weight: bold; margin-top: 0.8em; }
                ol { padding-left: 1.5em; }
                li { margin-bottom: 0.5em; }
                .h-usage-example, .ux-example { font-style: italic; display: block; margin-top: 0.5em; }
                .h-usage-example-translation { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; }
                a { color: #36c; text-decoration: none; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 0.85em; }
                table th, table td { border: 1px solid #a2a9b1; padding: 6px; text-align: center; }
                table th { background-color: #f8f9fa; }
                .example-checkbox { margin-right: 8px; vertical-align: middle; }""";
        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body>" + body + "</body></html>";
    }

    private void createCards() {
        /**
         * JS script to find selected examples and their context.
         */
        String js = """
                (() => {
                    const cards = [];
                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const container = cb.parentElement;
                        
                        // 1. Extract Swedish text (the phrase itself)
                        // If multiple possibilities, do '.className, tag, [prop=value]
                        const swedishEl = container.querySelector('.e-example');
                        const swedish = swedishEl ? swedishEl.innerText.trim() : null;
                        
                        // 2. Extract English translation
                        // We look for common translation classes used in Swedish Wiktionary entries
                        const englishEl = container.querySelector('.e-translation');
                        const english = englishEl ? englishEl.innerText.trim() : null;
                        
                        // 3. Extract the Word Meaning (the parent <li> text)
                        const li = container.closest('li');
                        let definition = null;
                        if (li) {
                            const clone = li.cloneNode(true);
                            // Remove nested lists (examples/sub-definitions) to get only the clean definition text
                            clone.querySelectorAll('dl, ul, ol').forEach(el => el.remove());
                            definition = clone.innerText.trim();
                        }
                        
                        // 4. Extract Grammatical Class (search upwards for the nearest H3/H4/H5)
                        let grammaticalClass = '';
                        let curr = li ? li.closest('ol') : container;
                        while (curr) {
                            curr = curr.previousElementSibling;
                            if (!curr) break;
                            const h = curr.matches('h3, h4, h5') ? curr : curr.querySelector('h3, h4, h5');
                            if (h) {
                                grammaticalClass = h.innerText.replace('[edit]', '').trim();
                                break;
                            }
                            if (curr.tagName === 'H2') break;
                        }
                        
                        cards.push({ swedish, english, definition, grammaticalClass });
                    });
                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
        webView.evaluateJavascript(js, null);
    }
}
