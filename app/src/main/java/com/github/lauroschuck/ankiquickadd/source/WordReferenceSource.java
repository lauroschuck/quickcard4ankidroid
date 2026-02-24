package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;

import com.github.lauroschuck.ankiquickadd.model.Language;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WordReferenceSource implements DictionarySource {
    private static final String TAG = "WordReferenceSource";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener) {
        // WordReference uses language pairs like 'enpt' (English to Portuguese)
        String langPair = sourceLanguage.getIsoCode() + targetLanguage.getIsoCode();
        String url = "https://www.wordreference.com/" + langPair + "/" + Uri.encode(word);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("WordReference returned code " + response.code());
                    return;
                }
                processResponse(response.body().string(), word, listener);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError("Network error: " + e.getMessage());
            }
        });
    }

    @Override
    public String getExtractionJs() {
        return "";
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            
            // 1. Identify the main content. WordReference usually has a #articleWRD
            Element article = doc.getElementById("articleWRD");
            if (article == null) {
                listener.onError("Translation not found on WordReference.");
                return;
            }

            // 2. Pre-process: remove ads and UI noise
            article.select(".ad-container, script, .share, .listen").remove();

            // 3. Inject checkboxes into examples
            // WordReference examples are often in <span class="ex"> or table rows with class "ex"
            for (Element ex : article.select("span.ex, .ex")) {
                ex.prepend("<input type='checkbox' class='example-checkbox'> ");
            }

            // 4. Build final HTML
            String finalHtml = buildHtmlPage(article.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing WordReference: " + e.getMessage());
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.4; color: #333; }
                .WRD { width: 100%; border-collapse: collapse; margin-bottom: 1em; }
                .WRD tr { border-bottom: 1px solid #eee; }
                .WRD .Fr { font-weight: bold; color: #000; padding: 4px; }
                .WRD .To { color: #444; padding: 4px; }
                .ex { font-style: italic; color: #666; display: block; margin-top: 4px; padding-left: 20px; }
                .example-checkbox { margin-right: 8px; vertical-align: middle; }
                h3 { background: #f4f4f4; padding: 5px; border-radius: 3px; font-size: 1.1em; }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
