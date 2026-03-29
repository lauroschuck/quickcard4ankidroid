package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ReversoSource implements DictionarySource {
    private static final String TAG = "ReversoSource";
    private final OkHttpClient client = new OkHttpClient();
    private Language lastLearningLanguage;
    private Language lastNativeLanguage;

    @Override
    public void fetch(
            @NonNull String word,
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            @NonNull OnResultListener listener) {
        this.lastLearningLanguage = learningLanguage;
        this.lastNativeLanguage = nativeLanguage;

        String langPath = learningLanguage.getDisplayName().toLowerCase() + "-"
                + nativeLanguage.getDisplayName().toLowerCase();
        String url = "https://dictionary.reverso.net/" + langPath + "/" + Uri.encode(word);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("Reverso returned code " + response.code());
                    return;
                }
                processResponse(response.body().string(), word, listener);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onError("Network error: " + e.getMessage());
            }
        });
    }

    /* @Override
    public void fetchMore(
            String word, Language learningLanguage, Language nativeLanguage, int page, OnResultListener listener) {
        // Reverso Context API endpoint for more examples
        String url = "https://context.reverso.net/bst-query-service";

        // Build JSON request body for Reverso API
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("learning_text", word);
        jsonBody.addProperty("native_text", "");
        jsonBody.addProperty("learning_lang", learningLanguage.getIsoCode());
        jsonBody.addProperty("native_lang", nativeLanguage.getIsoCode());
        jsonBody.addProperty("npage", page);
        jsonBody.addProperty("mode", 0);

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));

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
                        .append("<div class='src'><span class='text'>")
                        .append(src)
                        .append("</span></div>")
                        .append("<div class='trg'><span class='text'>")
                        .append(trg)
                        .append("</span></div>")
                        .append("</div>");
            }

            listener.onSuccess(html.toString(), word);
        } catch (Exception e) {
            listener.onError("Error parsing JSON: " + e.getMessage());
        }
    }
     */

    @Override
    public String getExtractionJs() {
        return """
                (() => {
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');

                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const container = cb.parentElement;
                        const learningEl = container.querySelector('.src .text, .example-src, .src');
                        const nativeEl = container.querySelector('.trg .text, .example-trg, .trg');

                        const learningText = learningEl ? learningEl.innerHTML.trim() : null;
                        const nativeText = nativeEl ? nativeEl.innerHTML.trim() : null;

                        let definition = '';
                        const entry = container.closest('.entry, .direction-cnt');
                        if (entry) {
                            const defEl = entry.querySelector('.description, .translation');
                            if (defEl) definition = defEl.innerText.trim();
                        }

                        cards.push({ headword, learningText, nativeText, definition, lexicalCategory: 'Reverso' });
                    });

                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
    }

    @Override
    public SelectedTextCards getCardsFromSelection(@NonNull String json) {
        List<TextNote.Input> cards = new Gson().fromJson(json, new TypeToken<List<TextNote.Input>>() {}.getType());
        var headword = cards.isEmpty() ? null : cards.get(0).headword();
        String sourceUrl = null;
        if (headword != null && lastLearningLanguage != null && lastNativeLanguage != null) {
            var langPath = lastLearningLanguage.getDisplayName().toLowerCase() + "-"
                    + lastNativeLanguage.getDisplayName().toLowerCase();
            sourceUrl = "https://dictionary.reverso.net/" + langPath + "/" + Uri.encode(headword);
        }
        return new SelectedTextCards(lastLearningLanguage, lastNativeLanguage, null, sourceUrl, cards);
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            Element mainContent = doc.selectFirst(".direction-cnt, #examples-content, .main-container");

            if (mainContent == null) {
                mainContent = doc.body();
            }

            mainContent
                    .select(".sharing-links, .favorite-button, .audio-button, .banner, script, style")
                    .remove();

            // Inject checkboxes into translation pairs (rows that have both learning and native markers)
            for (Element ex : mainContent.select("div.example, .example, .cd-example")) {
                // Verify it has both parts before adding checkbox
                boolean hasLearning = !ex.select(".src, .example-src").isEmpty();
                boolean hasNative = !ex.select(".trg, .example-trg").isEmpty();

                if (hasLearning
                        && hasNative
                        && ex.select("input.example-checkbox").isEmpty()) {
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
        String css =
                """
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

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css
                + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
