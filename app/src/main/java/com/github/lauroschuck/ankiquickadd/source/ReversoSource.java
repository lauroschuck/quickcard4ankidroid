package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;

import com.github.lauroschuck.ankiquickadd.model.Language;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ReversoSource implements DictionarySource {
    private static final String TAG = "ReversoSource";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener) {
        // Reverso Context uses full names in English: e.g., swedish-english
        String langPath = sourceLanguage.getDisplayName().toLowerCase() + "-" + targetLanguage.getDisplayName().toLowerCase();
        String url = "https://context.reverso.net/translation/" + langPath + "/" + Uri.encode(word);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("Reverso returned code " + response.code());
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
            
            // 1. Identify the examples section
            Element examplesSection = doc.getElementById("examples-content");
            if (examplesSection == null) {
                listener.onError("No examples found on Reverso.");
                return;
            }

            // 2. Pre-process: Inject checkboxes into each example container
            // Reverso examples are in div.example
            for (Element ex : examplesSection.select("div.example")) {
                ex.prepend("<input type='checkbox' class='example-checkbox'> ");
            }

            // 3. Build final HTML
            String finalHtml = buildHtmlPage(examplesSection.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing Reverso: " + e.getMessage());
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                .example { margin-bottom: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 1em; }
                .src { display: block; font-weight: bold; margin-bottom: 0.3em; }
                .trg { display: block; color: #54595d; }
                .text { display: inline; }
                em { background-color: #fff9c4; font-style: normal; font-weight: bold; }
                .example-checkbox { margin-right: 12px; float: left; transform: scale(1.2); }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
