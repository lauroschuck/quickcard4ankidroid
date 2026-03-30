package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

    @Override
    public String getExtractionJs() {
        return """
                (() => {
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');

                    document.querySelectorAll('input.example-checkbox:checked').forEach(cb => {
                        const exampleEl = cb.closest('app-translation-example');
                        if (!exampleEl) return;

                        const sourceEl = exampleEl.querySelector('.translation-example__source p');
                        const targetEl = exampleEl.querySelector('.translation-example__target p');

                        const learningText = sourceEl ? sourceEl.innerHTML.trim() : null;
                        const nativeText = targetEl ? targetEl.innerHTML.trim() : null;

                        // Find definition
                        const contextEl = exampleEl.closest('app-translation-context');
                        let definition = '';
                        if (contextEl) {
                            const defLink = contextEl.querySelector('app-link-internal');
                            if (defLink) definition = defLink.innerText.trim();
                        }

                        // Find lexical category
                        let lexicalCategory = 'REVERSO';
                        let searchEl = contextEl || exampleEl;
                        let prev = searchEl.previousElementSibling;
                        while (prev) {
                            if (prev.tagName.toLowerCase() === 'app-translation-pos-chips') {
                                lexicalCategory = prev.innerText.trim();
                                break;
                            }
                            prev = prev.previousElementSibling;
                        }

                        cards.push({ headword, learningText, nativeText, definition, lexicalCategory });
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

    @Override
    public void handleSourceAction(String url, WebView webView) {
        Uri uri = Uri.parse(url);
        if ("more".equals(uri.getLastPathSegment())) {
            String def = uri.getQueryParameter("def");
            String word = uri.getQueryParameter("word");
            String ctxId = uri.getQueryParameter("ctx");
            fetchMoreExamples(word, def, ctxId, webView);
        }
    }

    private void fetchMoreExamples(String word, String def, String ctxId, WebView webView) {
        if (lastLearningLanguage == null || lastNativeLanguage == null) return;

        String url = String.format(
                "https://context.reverso.net/bst-query-service?source_lang=%s&target_lang=%s&target_text=%s&source_text=%s&max_results=1&npage=1&json=1&pos_reorder=5",
                lastLearningLanguage.getIsoCode().toLowerCase(),
                lastNativeLanguage.getIsoCode().toLowerCase(),
                Uri.encode(def),
                Uri.encode(word));

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .header("Referer", "https://context.reverso.net/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    webView.post(() -> injectExamples(json, ctxId, webView));
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch more examples", e);
            }
        });
    }

    private void injectExamples(String json, String ctxId, WebView webView) {
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            JsonArray list = obj.getAsJsonArray("list");
            if (list == null || list.isEmpty()) {
                return;
            }

            StringBuilder htmlBuilder = new StringBuilder();
            // Skip the first element, it is the first example already in the HTML
            for (int i = 1; i < list.size(); i++) {
                JsonObject item = list.get(i).getAsJsonObject();
                String sText = item.get("s_text").getAsString();
                String tText = item.get("t_text").getAsString();

                // Replace em with b for consistency with existing styling
                sText = convertEmToB(sText);
                tText = convertEmToB(tText);

                htmlBuilder
                        .append("<app-translation-example>")
                        .append("<input type='checkbox' class='example-checkbox'>")
                        .append("<div class='translation-example__source'><p>")
                        .append(sText)
                        .append("</p></div>")
                        .append("<div class='translation-example__target'><p>")
                        .append(tText)
                        .append("</p></div>")
                        .append("</app-translation-example>");
            }

            String escapedHtml = htmlBuilder.toString().replace("'", "\\'");
            String js = String.format(
                    "(() => {"
                            + "  const ctx = document.getElementById('%s');"
                            + "  if (ctx) {"
                            + "    const newExamples = document.createRange().createContextualFragment('%s');"
                            + "    ctx.appendChild(newExamples);"
                            + "    const btn = ctx.querySelector('.more-examples-btn');"
                            + "    if (btn) btn.remove();"
                            + "  }"
                            + "})();",
                    ctxId, escapedHtml);

            webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting examples", e);
        }
    }

    private String convertEmToB(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element em : doc.select("em")) {
            em.tagName("b");
        }
        return doc.body().html();
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            Element mainContent = doc.selectFirst(".main-container app-context-list .main-content");

            if (mainContent == null) {
                mainContent = doc.body();
            }

            mainContent
                    .select(
                            "app-result-title, "
                                    + "app-translation-pos-block, .translation-context__actions, .translation-example__actions_mobile, "
                                    + "app-translation-see-more, .context-list__other-container, .context-list__suggestions-container")
                    .remove();

            // Erase more tags inside the chips that ruin design
            for (Element posChips : mainContent.select("app-translation-pos-chips")) {
                var text = posChips.text();
                posChips.empty().text(text);
            }

            // Inject checkboxes into examples and replace EM with B
            for (Element example : mainContent.select("app-translation-example")) {
                if (example.select("input.example-checkbox").isEmpty()) {
                    example.prepend("<input type='checkbox' class='example-checkbox'> ");
                }
                for (Element em : example.select("em")) {
                    em.tagName("b");
                }
            }

            // Add [more] button to each context
            int contextId = 0;
            for (Element context : mainContent.select("app-translation-context")) {
                context.attr("id", "ctx-" + contextId);
                Element defLink = context.selectFirst("app-link-internal");
                if (defLink != null) {
                    String def = defLink.text();
                    defLink.after(" <a class='more-examples-btn' href='app://source/more?def=" + Uri.encode(def)
                            + "&word=" + Uri.encode(word) + "&ctx=ctx-" + contextId + "'>More</a>");
                    defLink.empty().text(def);
                }
                contextId++;
            }

            String langPath = lastLearningLanguage.getDisplayName().toLowerCase() + "-"
                    + lastNativeLanguage.getDisplayName().toLowerCase();
            String sourceUrl = "https://dictionary.reverso.net/" + langPath + "/" + Uri.encode(word);
            String headerHtml = String.format(
                    "<h2>%s (%s) <a href='%s' class='reverso-link' target='_blank'>R</a></h2>",
                    word, lastLearningLanguage.getDisplayName(), sourceUrl);

            String finalHtml = buildHtmlPage(headerHtml + mainContent.html(), word);
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
                h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; display: flex; align-items: center; }
                .reverso-link { text-decoration: none; color: #36c; margin-left: 12px; font-size: 0.6em; font-weight: bold; border: 1px solid #36c; padding: 0 6px; border-radius: 4px; background: #f0f7ff; vertical-align: middle; }
                app-translation-pos-chips { display: inline-block; background: #36c; color: white; padding: 0.2em 1em; margin: 10px 0 5px 0; font-weight: bold; border-radius: 4px; text-decoration: none !important; line-height: normal; }
                app-translation-context { display: block; border-bottom: 1px solid #eee; padding: 10px 0; }
                app-link-internal { display: inline-block; color: #202122; font-weight: bold; margin-bottom: 5px; font-size: 1.1em; text-decoration: none !important; pointer-events: none; }
                .more-examples-btn { font-size: 0.8em; color: #36c; text-decoration: none; margin-left: 10px; border: 1px solid #36c; padding: 0 4px; border-radius: 4px; vertical-align: middle; }
                app-translation-example { display: block; margin-left: 30px; margin-bottom: 10px; position: relative; }
                .translation-example__source { font-style: italic; display: block; margin-bottom: 2px; }
                .translation-example__target { color: #54595d; display: block; font-size: 0.9em; }
                .translation-example__source p, .translation-example__target p { margin: 0; }
                .example-checkbox { position: absolute; left: -30px; top: 2px; transform: scale(1.2); }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css
                + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
