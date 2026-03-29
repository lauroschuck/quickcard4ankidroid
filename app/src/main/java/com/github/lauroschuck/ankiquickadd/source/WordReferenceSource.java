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

public class WordReferenceSource implements DictionarySource {
    private static final String TAG = "WordReferenceSource";
    private final OkHttpClient client = new OkHttpClient();
    private Language lastLearningLanguage;
    private Language lastNativeLanguage;

    @Override
    public void fetch(String word, Language learningLanguage, Language nativeLanguage, OnResultListener listener) {
        this.lastLearningLanguage = learningLanguage;
        this.lastNativeLanguage = nativeLanguage;
        String langPair = learningLanguage.getIsoCode() + nativeLanguage.getIsoCode();
        String url = "https://www.wordreference.com/" + langPair + "/" + Uri.encode(word);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("WordReference returned code " + response.code());
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
                        const frRow = cb.closest('tr');
                        if (!frRow) return;

                        const toRow = frRow.nextElementSibling;
                        if (!toRow) return;

                        const sourceTextEl = frRow.querySelector('.FrEx');
                        const nativeTextEl = toRow.querySelector('.ToEx');

                        if (!sourceTextEl || !nativeTextEl) return;

                        const learningText = sourceTextEl.innerHTML.trim();
                        const nativeText = nativeTextEl.innerHTML.trim();

                        // Find the definition (DS class) by looking back through preceding rows
                        let definition = '';
                        let curr = frRow;
                        while (curr) {
                            const defEl = curr.querySelector('.DS');
                            if (defEl) {
                                definition = defEl.innerText.trim();
                                break;
                            }
                            curr = curr.previousElementSibling;
                        }

                        cards.push({ headword, learningText, nativeText, definition, lexicalCategory: 'WordReference' });
                    });

                    Android.processSelectedCards(JSON.stringify(cards));
                })();""";
    }

    @Override
    public SelectedTextCards getCardsFromSelection(String json) {
        List<TextNote.Input> cards = new Gson().fromJson(json, new TypeToken<List<TextNote.Input>>() {}.getType());
        String headword = cards.isEmpty() ? null : cards.get(0).headword();
        String sourceUrl = null;
        if (headword != null && lastLearningLanguage != null && lastNativeLanguage != null) {
            String langPair = lastLearningLanguage.getIsoCode() + lastNativeLanguage.getIsoCode();
            sourceUrl = "https://www.wordreference.com/" + langPair + "/" + Uri.encode(headword);
        }
        return new SelectedTextCards(lastLearningLanguage, lastNativeLanguage, null, sourceUrl, cards);
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            Element article = doc.getElementById("articleWRD");
            if (article == null) {
                listener.onError("Translation not found on WordReference.");
                return;
            }

            // Clean up UI noise
            article.select(".ad-container, script, .share, .listen, .header-tool, .rc")
                    .remove();

            // Target the specific translation tables
            for (Element table : article.select("table.WRD")) {
                for (Element row : table.select("tr")) {
                    boolean isFrEx = !row.select("td.FrEx").isEmpty();

                    if (isFrEx) {
                        Element nextRow = row.nextElementSibling();
                        boolean hasToExNext =
                                nextRow != null && !nextRow.select("td.ToEx").isEmpty();

                        if (hasToExNext) {
                            row.prepend(
                                    "<td class='checkbox-cell'><input type='checkbox' class='example-checkbox'></td>");
                        } else {
                            row.prepend("<td class='checkbox-cell'></td>");
                        }
                    } else {
                        // All other rows (ToEx, FrWrd, etc) get an empty cell to keep columns aligned
                        row.prepend("<td class='checkbox-cell'></td>");
                    }
                }
            }

            String finalHtml = buildHtmlPage(article.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing WordReference: " + e.getMessage());
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String css =
                """
                body { font-family: sans-serif; padding: 12px; line-height: 1.4; color: #333; }
                .WRD { width: 100%; border-collapse: collapse; margin-bottom: 1em; font-size: 0.9em; }
                .WRD tr { border-bottom: 1px solid #eee; }
                .WRD td { padding: 4px; vertical-align: top; }
                .WRD .Fr, .WRD .FrEx, .WRD .Fr3 { font-weight: bold; color: #000; }
                .WRD .To, .WRD .ToEx, .WRD .To3 { color: #444; }
                .ex { font-style: italic; color: #666; display: block; margin-top: 2px; }
                .example-checkbox { transform: scale(1.2); margin: 0; }
                .checkbox-cell { width: 30px; text-align: center; vertical-align: middle !important; }
                .DS { color: #888; font-size: 0.85em; font-style: italic; }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css
                + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
