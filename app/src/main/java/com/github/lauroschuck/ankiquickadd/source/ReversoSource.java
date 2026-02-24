package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;

import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReversoSource implements DictionarySource {
    private static final String TAG = "ReversoSource";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener) {
        String langPath = sourceLanguage.getDisplayName().toLowerCase() + "-" + targetLanguage.getDisplayName().toLowerCase();
        String url = "https://dictionary.reverso.net/" + langPath + "/" + Uri.encode(word);

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
    public void fetchMore(String word, Language sourceLanguage, Language targetLanguage, int page, OnResultListener listener) {
        // Reverso Context API endpoint for more examples
        String url = "https://context.reverso.net/bst-query-service";
        
        // Build JSON request body for Reverso API
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("source_text", word);
        jsonBody.addProperty("target_text", "");
        jsonBody.addProperty("source_lang", sourceLanguage.getIsoCode());
        jsonBody.addProperty("target_lang", targetLanguage.getIsoCode());
        jsonBody.addProperty("npage", page);
        jsonBody.addProperty("mode", 0);

        RequestBody body = RequestBody.create(
                jsonBody.toString(), 
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .header("Referer", "https://context.reverso.net/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("Reverso API returned code " + response.code());
                    return;
                }
                processJsonResponse(response.body().string(), word, listener);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError(e.getMessage());
            }
        });
    }

    private void processJsonResponse(String json, String word, OnResultListener listener) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray list = obj.getAsJsonArray("list");
            StringBuilder html = new StringBuilder();
            
            for (int i = 0; i < list.size(); i++) {
                JsonObject ex = list.get(i).getAsJsonObject();
                String src = ex.get("s").getAsString();
                String trg = ex.get("t").getAsString();
                
                html.append("<div class='example'>")
                    .append("<input type='checkbox' class='example-checkbox'> ")
                    .append("<div class='src'><span class='text'>").append(src).append("</span></div>")
                    .append("<div class='trg'><span class='text'>").append(trg).append("</span></div>")
                    .append("</div>");
            }
            
            listener.onSuccess(html.toString(), word);
        } catch (Exception e) {
            listener.onError("Error parsing JSON: " + e.getMessage());
        }
    }

    @Override
    public String getExtractionJs() {
        return """
                (() => {
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');
                    
                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const container = cb.parentElement;
                        const sourceEl = container.querySelector('.src .text, .example-src, .src');
                        const targetEl = container.querySelector('.trg .text, .example-trg, .trg');
                        
                        const sourceText = sourceEl ? sourceEl.innerHTML.trim() : null;
                        const targetText = targetEl ? targetEl.innerHTML.trim() : null;
                        
                        let definition = '';
                        const entry = container.closest('.entry, .direction-cnt');
                        if (entry) {
                            const defEl = entry.querySelector('.description, .translation');
                            if (defEl) definition = defEl.innerText.trim();
                        }
                        
                        cards.push({ headword, sourceText, targetText, definition, lexicalCategory: 'Reverso' });
                    });
                    
                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            Element mainContent = doc.selectFirst(".direction-cnt, #examples-content, .main-container");

            if (mainContent == null) {
                mainContent = doc.body();
            }

            mainContent.select(".sharing-links, .favorite-button, .audio-button, .banner, script, style").remove();

            // Inject checkboxes into translation pairs (rows that have both source and target markers)
            for (Element ex : mainContent.select("div.example, .example, .cd-example")) {
                // Verify it has both parts before adding checkbox
                boolean hasSource = !ex.select(".src, .example-src").isEmpty();
                boolean hasTarget = !ex.select(".trg, .example-trg").isEmpty();
                
                if (hasSource && hasTarget && ex.select("input.example-checkbox").isEmpty()) {
                    ex.prepend("<input type='checkbox' class='example-checkbox'> ");
                }
            }

            String finalHtml = buildHtmlPage(mainContent.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing Reverso Dictionary: " + e.getMessage());
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                .example, .cd-example { margin-bottom: 1.2em; border-bottom: 1px solid #eee; padding-bottom: 0.8em; overflow: hidden; }
                .src, .example-src { display: block; font-weight: bold; margin-bottom: 0.2em; }
                .trg, .example-trg { display: block; color: #54595d; }
                .text { display: inline; }
                em { background-color: #fff9c4; font-style: normal; font-weight: bold; }
                .example-checkbox { margin-right: 12px; float: left; transform: scale(1.2); margin-top: 4px; }
                .description { color: #36c; font-weight: bold; margin-bottom: 0.5em; display: block; }
                h3 { border-bottom: 1px solid #a2a9b1; margin-top: 1em; }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
