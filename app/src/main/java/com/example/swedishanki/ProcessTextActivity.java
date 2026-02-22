package com.example.swedishanki;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.statusText);
        createCardsButton = findViewById(R.id.createCardsButton);

        // Custom WebViewClient to intercept whitelisted links
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("app://fetch/")) {
                    // Decode the word (e.g., sm%C3%A5 -> små) before fetching
                    String encodedWord = url.substring("app://fetch/".length());
                    String word = Uri.decode(encodedWord);
                    fetchWiktionaryHtml(word);
                    return true;
                }
                // Block all other link clicks
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

    private void fetchWiktionaryHtml(String word) {
        runOnUiThread(() -> {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Fetching '" + word + "'...");
            createCardsButton.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
        });
        
        String url = "https://en.wiktionary.org/w/api.php?action=parse"
                + "&prop=text&format=json&redirects=1"
                + "&page=" + Uri.encode(word);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "SwedishAnkiQuickAdd/1.0 (Contact: x34689@gmail.com)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                processHtmlResponse(body);
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
                runOnUiThread(() -> statusText.setText("Word not found on Wiktionary."));
                return;
            }

            String rawHtml = obj.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
            Document doc = Jsoup.parse(rawHtml);

            Element parserOutput = doc.selectFirst(".mw-parser-output");
            if (parserOutput == null) parserOutput = doc.body();

            // Find Swedish header
            Element swedishHeader = parserOutput.selectFirst(".mw-heading2:has(#Swedish), h2:has(#Swedish)");
            if (swedishHeader == null) {
                Element swedishId = parserOutput.selectFirst("#Swedish");
                if (swedishId != null) {
                    swedishHeader = swedishId.closest(".mw-heading2, h2");
                }
            }

            if (swedishHeader == null) {
                runOnUiThread(() -> statusText.setText("No Swedish section found."));
                return;
            }

            // Extract content between this H2 and the next H2
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append(swedishHeader.outerHtml());
            
            Element next = swedishHeader.nextElementSibling();
            while (next != null && !next.hasClass("mw-heading2") && !next.tagName().equalsIgnoreCase("h2")) {
                contentBuilder.append(next.outerHtml());
                next = next.nextElementSibling();
            }

            // Process links: whitelist Swedish wiki links and turn others into plain text
            Document contentDoc = Jsoup.parseBodyFragment(contentBuilder.toString());
            for (Element a : contentDoc.select("a")) {
                String href = a.attr("href");
                if (href.contains("/wiki/") && href.contains("#Swedish")) {
                    // Extract the word from /wiki/word#Swedish
                    int start = href.indexOf("/wiki/") + 6;
                    int end = href.indexOf("#Swedish");
                    if (end > start) {
                        String word = href.substring(start, end);
                        a.attr("href", "app://fetch/" + word);
                    } else {
                        a.unwrap();
                    }
                } else {
                    // Turn other links into plain text
                    a.unwrap();
                }
            }

            // Add checkboxes to examples
            for (Element ex : contentDoc.select(".h-usage-example, .ux-example")) {
                ex.prepend("<input type=\"checkbox\" class=\"example-checkbox\"> ");
            }

            String processedHtml = contentDoc.body().html();

            // Base CSS to mimic Wiktionary
            String css = "body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; } " +
                         "h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; } " +
                         "h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; } " +
                         "h4 { font-size: 1.15em; font-weight: bold; margin-top: 1.0em; } " +
                         "h5 { font-size: 1.05em; font-weight: bold; margin-top: 0.8em; } " +
                         "ol { padding-left: 1.5em; } " +
                         "li { margin-bottom: 0.5em; } " +
                         ".h-usage-example, .ux-example { font-style: italic; display: block; margin-top: 0.5em; } " +
                         ".h-usage-example-translation, .mention-gloss { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; } " +
                         ".mw-editsection { display: none; } " +
                         "a { color: #36c; text-decoration: none; } " +
                         "table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 0.85em; background-color: #fff; } " +
                         "table th, table td { border: 1px solid #a2a9b1; padding: 6px; text-align: center; } " +
                         "table th { background-color: #f8f9fa; font-weight: bold; } " +
                         ".example-checkbox { margin-right: 8px; vertical-align: middle; }";

            String finalHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                               "<style>" + css + "</style></head><body>" + processedHtml + "</body></html>";

            runOnUiThread(() -> {
                statusText.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadDataWithBaseURL("https://en.wiktionary.org/", finalHtml, "text/html", "UTF-8", null);
                createCardsButton.setVisibility(View.VISIBLE);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing HTML", e);
            runOnUiThread(() -> statusText.setText("Error: " + e.getMessage()));
        }
    }

    private void createCards() {
        Toast.makeText(this, "Checkbox integration coming soon...", Toast.LENGTH_SHORT).show();
    }
}
