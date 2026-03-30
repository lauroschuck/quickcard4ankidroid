package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
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

                    const posMapping = {
                        'n': 'noun',
                        'npl': 'plural noun',
                        'n as adj': 'noun as adjective',
                        'v': 'verb',
                        'adj': 'adjective',
                        'adv': 'adverb',
                        'prep': 'preposition',
                        'pron': 'pronoun',
                        'conj': 'conjunction',
                        'vi': 'intransitive verb',
                        'vtr': 'transitive verb',
                        'vi phrasal': 'phrasal verb, intransitive',
                        'vi phrasal insep': 'phrasal verb, intransitive, inseparable',
                        'v aux': 'auxiliary verb',
                        'v aux insep': 'auxiliary verb, inseparable',
                        'v expr': 'verbal expression',
                        'expr': 'expression'
                    };

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

                        let definition = '';
                        let lexicalCategory = '';
                        let curr = frRow;
                        while (curr) {
                            const frWrd = curr.querySelector('.FrWrd');
                            if (frWrd) {
                                console.log('Processing WR entry for headword:', headword);

                                const strong = frWrd.querySelector('strong');
                                if (strong) {
                                    definition = strong.innerText.trim();
                                }

                                // Expand definition with columns between .FrWrd and .ToWrd that have no class
                                const children = Array.from(curr.children);
                                const fIdx = children.findIndex(c => c.classList.contains('FrWrd'));
                                const tIdx = children.findIndex(c => c.classList.contains('ToWrd'));
                                if (fIdx !== -1 && tIdx !== -1) {
                                    for (let k = fIdx + 1; k < tIdx; k++) {
                                        const col = children[k];
                                        if (!col.className) {
                                            const val = col.innerText.trim();
                                            if (val) {
                                                definition += (definition ? ' ' : '') + val;
                                            }
                                        }
                                    }
                                }

                                // Extract POS (lexical category)
                                const posEm = frWrd.querySelector('em.POS2[data-abbr]');
                                if (posEm) {
                                    lexicalCategory = posEm.textContent;
                                    if (lexicalCategory) {
                                        const categories = lexicalCategory.split(/ *\\+ */);
                                        lexicalCategory = categories.map(function(cat) {
                                            return posMapping[cat] || cat;
                                        }).join(' + ');
                                    }
                                }
                                console.log('Extracted Definition:', definition, 'LexicalCategory:', lexicalCategory);
                                break;
                            }
                            curr = curr.previousElementSibling;
                        }

                        cards.push({
                            headword,
                            learningText,
                            nativeText,
                            definition: definition,
                            lexicalCategory: lexicalCategory
                        });
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

            // Detect virtual dictionary
            String virtualDictionaryWarning =
                    article.selectFirst(".wrcopyright").text().contains("Virtual Dictionary")
                            ? """
                            <div class="virtual-dictionary">
                                Note that this is a virtual dictionary, using an intermediary dictionary (usually English).<br/>\
                                Quality may be lower as a result.
                            </div>
                            """
                            : "";

            // Clean up UI noise
            article.select(".ad-container, script, .share, .listen, .header-tool, .rc, .wrcopyright, .WRreporterror")
                    .remove();

            // Target the specific translation tables
            for (Element table : article.select("table.WRD")) {
                List<Element> rows = table.select("tr");
                int i = 0;
                while (i < rows.size()) {
                    // Find a block of rows for one entry. An entry starts with a row containing .FrWrd
                    List<Element> block = new ArrayList<>();
                    block.add(rows.get(i));
                    int j = i + 1;
                    while (j < rows.size() && rows.get(j).select("td.FrWrd").isEmpty()) {
                        block.add(rows.get(j));
                        j++;
                    }

                    processExampleBlock(block, word);
                    i = j;
                }
            }

            String finalHtml = buildHtmlPage(virtualDictionaryWarning + article.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing WordReference: " + e.getMessage());
        }
    }

    private void processExampleBlock(List<Element> block, String headword) {
        List<Element> exampleRows = new ArrayList<>();
        for (Element row : block) {
            if (row.hasClass("langHeader")) {
                continue;
            }
            if (!row.select("td.FrEx").isEmpty() || !row.select("td.ToEx").isEmpty()) {
                exampleRows.add(row);
            }
        }

        // Only valid if we have alternating pairs of FrEx and ToEx
        boolean valid = !exampleRows.isEmpty() && exampleRows.size() % 2 == 0;
        if (valid) {
            for (int k = 0; k < exampleRows.size(); k += 2) {
                Element fr = exampleRows.get(k);
                Element to = exampleRows.get(k + 1);
                if (fr.select("td.FrEx").isEmpty() || to.select("td.ToEx").isEmpty()) {
                    valid = false;
                    break;
                }
            }
        }

        // Extract the entry's headword from the first row of the block (.FrWrd)
        String entryHeadword = "";
        Element toWrdEl = block.get(0).selectFirst("td.ToWrd");
        if (toWrdEl != null) {
            // Take only the text of this element, ignoring its children
            entryHeadword = toWrdEl.ownText().trim();
        }

        boolean firstRealRowFound = false;
        for (Element row : block) {
            if (row.hasClass("langHeader")) {
                row.prepend("<td class='checkbox-cell'></td>");
                continue;
            }

            if (valid && !row.select("td.FrEx").isEmpty()) {
                row.prepend("<td class='checkbox-cell'><input type='checkbox' class='example-checkbox'></td>");
                if (!entryHeadword.equalsIgnoreCase(headword)) {
                    String alert =
                            "<span title='Entry word mismatch: " + entryHeadword + "' style='cursor:help;'>⚠️</span> ";
                    if (row.childrenSize() > 1) {
                        row.child(1).prepend(alert);
                    }
                }
            } else {
                row.prepend("<td class='checkbox-cell'></td>");
            }

            if (!valid) {
                row.addClass("no-examples");
                if (!firstRealRowFound) {
                    row.addClass("main-row");
                    firstRealRowFound = true;
                }
            }
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String filterHtml =
                """
            <div style='margin-bottom: 15px; background: #f0f4f8; padding: 10px; border-radius: 4px; display: flex; align-items: center; font-size: 0.9em; border: 1px solid #d1d9e0;'>
                <input type='checkbox' id='filter-toggle' checked onchange='document.body.classList.toggle("filter-active", this.checked)' style='margin-right: 10px; transform: scale(1.2);'>
                <label for='filter-toggle' style='font-weight: bold; color: #24292f;'>Hide entries without valid examples</label>
            </div>
            """;

        String css =
                """
                body { font-family: sans-serif; padding: 12px; line-height: 1.4; color: #333; }
                body.filter-active tr.no-examples.main-row td:nth-child(n+3) { font-size: 0; }
                body.filter-active tr.no-examples:not(.main-row) { display: none; }
                .virtual-dictionary { margin-bottom: 15px; background: #d1d4b0; padding: 10px; border-radius: 4px; display: flex; align-items: left; border: 1px solid #cc2f21; }
                .WRD { width: 100%; border-collapse: collapse; margin-bottom: 1em; font-size: 0.9em; }
                .WRD tr.odd { background-color: #f6f6f6; }
                .WRD tr.even { background-color: #ffffff; }
                .WRD td { padding: 6px 4px; vertical-align: top; }
                .WRD .Fr, .WRD .FrEx, .WRD .Fr3 { font-weight: bold; color: #000; }
                .WRD .To, .WRD .ToEx, .WRD .To3 { color: #444; }
                .ex { font-style: italic; color: #666; display: block; margin-top: 2px; }
                .example-checkbox { transform: scale(1.2); margin: 0; }
                .checkbox-cell { width: 30px; text-align: center; vertical-align: middle !important; }
                .DS { color: #888; font-size: 0.85em; font-style: italic; }
                .wrtopsection { background-color: #36c; color: #fff; font-weight: bold; padding: 4px 8px !important; }
                """;

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css
                + "</style></head><body data-word='" + word + "' class='filter-active'>" + filterHtml + bodyContent
                + "</body></html>";
    }
}
