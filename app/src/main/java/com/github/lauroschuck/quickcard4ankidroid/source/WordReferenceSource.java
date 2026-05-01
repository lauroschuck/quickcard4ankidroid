package com.github.lauroschuck.quickcard4ankidroid.source;

import android.net.Uri;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.DictionaryNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.TextNote;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import timber.log.Timber;

public class WordReferenceSource implements DataSource {
    private final OkHttpClient client = new OkHttpClient();
    private Language lastLearningLanguage;
    private Language lastNativeLanguage;

    @Override
    public String getName() {
        return "WordReference";
    }

    @Override
    public void fetch(String word, Language learningLanguage, Language nativeLanguage, OnResultListener listener) {
        this.lastLearningLanguage = learningLanguage;
        this.lastNativeLanguage = nativeLanguage;
        String langPair = learningLanguage.getIsoCode() + nativeLanguage.getIsoCode();
        String url = "https://www.wordreference.com/" + langPair + "/" + Uri.encode(word);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "QuickCard4AnkiDroid/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (response.code() == 404) {
                        listener.onNotFound();
                    } else {
                        listener.onError("HTTP " + response.code(), null);
                    }
                    return;
                }
                processResponse(response.body().string(), word, listener);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onError("I/O: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public String getExtractionJs() {
        return """
                (() => {
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');
                    const isDefinitions = document.body.classList.contains('mode-definitions');

                    // Collapse the lexical categories into a simpler definition
                    const posMapping = {
                        'n': 'noun',
                        'npl': 'noun', // noun plural
                        'n as adj': 'adjective', // noun as adjective
                        'v': 'verb', // verb
                        'adj': 'adjective', // adjective
                        'adv': 'adverb', // adverb
                        'prep': 'preposition', // preposition
                        'pron': 'pronoun', // pronoun
                        'conj': 'conjunction', // conjunction
                        'vi': 'verb', // intransitive verb
                        'vtr': ' verb', // transitive verb
                        'vi phrasal': 'verb', // phrasal verb, intransitive
                        'vi phrasal insep': 'verb', // phrasal verb, intransitive, inseparable
                        'v aux': 'verb', // auxiliary verb
                        'v aux insep': 'verb', // auxiliary verb, inseparable
                        'v expr': 'expression', // verbal expression
                        'expr': 'expression'
                    };

                    if (isDefinitions) {
                        const entries = [];
                        document.querySelectorAll('.sense-checkbox:checked').forEach(cb => {
                            const row = cb.closest('tr');
                            const blockId = row.getAttribute('data-block-id');
                            const headerRow = document.getElementById(blockId);
                            if (!headerRow) return;

                            const radio = document.querySelector('input[name="radio-' + blockId + '"]:checked');

                            let definition = '';
                            let lexicalCategory = '';

                            const frWrdHeader = headerRow.querySelector('.FrWrd');
                            if (frWrdHeader) {
                                const strong = frWrdHeader.querySelector('strong');
                                if (strong) definition = strong.innerText.trim();

                                const posEm = frWrdHeader.querySelector('em.POS2');
                                if (posEm) {
                                    const rawPos = posEm.innerText.trim();
                                    if (rawPos) {
                                        lexicalCategory = rawPos.split(/ *\\\\+ */).map(function(cat) {
                                            const cleaned = cat.toLowerCase().trim();
                                            return posMapping[cleaned] || cat;
                                        }).join(' + ');
                                    }
                                }
                            }

                            // Append content of the specific row's sense column (index 2)
                            if (row.children.length >= 3) {
                                const senseCol = row.children[2];
                                const extra = senseCol.innerText.trim();
                                if (extra) {
                                    definition += (definition ? ' ' : '') + extra;
                                }
                            }

                            let learningText = null;
                            let nativeText = null;
                            if (radio) {
                                const exRow = radio.closest('tr');
                                const trgRow = exRow.nextElementSibling;
                                const srcEl = exRow.querySelector('.FrEx');
                                const trgEl = trgRow ? trgRow.querySelector('.ToEx') : null;
                                learningText = srcEl ? srcEl.innerHTML.trim() : null;
                                nativeText = trgEl ? trgEl.innerHTML.trim() : null;
                            }

                            entries.push({ definition, lexicalCategory: lexicalCategory, learningText, nativeText });
                        });
                        Android.processSelectedCards(JSON.stringify({ mode: 'definitions', headword: headword, entries: entries }));
                    } else {
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

                                    const posEm = frWrd.querySelector('em.POS2');
                                    if (posEm) {
                                        const rawPos = posEm.innerText.trim();
                                        if (rawPos) {
                                            lexicalCategory = rawPos.split(/ *\\\\+ */).map(function(cat) {
                                                const cleaned = cat.toLowerCase().trim();
                                                return posMapping[cleaned] || cat;
                                            }).join(' + ');
                                        }
                                    }
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
                        Android.processSelectedCards(JSON.stringify({ mode: 'examples', headword: headword, cards: cards }));
                    }
                })();""";
    }

    @Override
    public SelectedCards<? extends com.github.lauroschuck.quickcard4ankidroid.anki.notes.AbstractAnkiNote.Input>
            getCardsFromSelection(String json) {
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        String mode = obj.has("mode") ? obj.get("mode").getAsString() : "examples";
        String headword = obj.has("headword") ? obj.get("headword").getAsString() : null;

        Uri sourceUrl = null;
        if (headword != null && lastLearningLanguage != null && lastNativeLanguage != null) {
            String langPair = lastLearningLanguage.getIsoCode() + lastNativeLanguage.getIsoCode();
            sourceUrl = Uri.parse("https://www.wordreference.com/" + langPair + "/" + Uri.encode(headword));
        }

        if ("definitions".equals(mode)) {
            JsonArray entriesArr = obj.getAsJsonArray("entries");
            List<Map<String, String>> entries =
                    gson.fromJson(entriesArr, new TypeToken<List<Map<String, String>>>() {}.getType());

            if (entries.isEmpty()) {
                return null;
            }

            Map<String, List<Map<String, String>>> grouped =
                    entries.stream().collect(Collectors.groupingBy(e -> e.getOrDefault("lexicalCategory", "")));

            List<DictionaryNote.Input> inputs = new ArrayList<>();
            for (var entry : grouped.entrySet()) {
                String lexicalCat = entry.getKey();
                List<DictionaryNote.Input.Definition> definitions = entry.getValue().stream()
                        .map(e -> new DictionaryNote.Input.Definition(
                                e.get("definition"), e.get("learningText"), e.get("nativeText")))
                        .collect(Collectors.toList());

                inputs.add(new DictionaryNote.Input(headword != null ? headword : "", null, lexicalCat, definitions));
            }
            return new SelectedDictionaryCards(lastLearningLanguage, lastNativeLanguage, null, sourceUrl, inputs);
        } else {
            JsonArray cardsArr = obj.has("cards") ? obj.getAsJsonArray("cards") : new JsonArray();
            List<TextNote.Input> cards = gson.fromJson(cardsArr, new TypeToken<List<TextNote.Input>>() {}.getType());
            return new SelectedTextCards(lastLearningLanguage, lastNativeLanguage, null, sourceUrl, cards);
        }
    }

    private void processResponse(String html, String word, OnResultListener listener) {
        try {
            Document doc = Jsoup.parse(html);
            Element article = doc.getElementById("articleWRD");
            if (article == null) {
                listener.onNotFound();
                return;
            }

            // Detect virtual dictionary
            String virtualDictionaryWarning = article.selectFirst(".wrcopyright") != null
                            && article.selectFirst(".wrcopyright").text().contains("Virtual Dictionary")
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
            int blockId = 0;
            for (Element table : article.select("table.WRD")) {
                List<Element> rows = table.select("tr");
                int i = 0;
                while (i < rows.size()) {
                    Element currentRow = rows.get(i);
                    // Skip and align headers
                    if (currentRow.hasClass("wrtopsection") || currentRow.hasClass("langHeader")) {
                        currentRow.prepend("<td class='checkbox-cell left-cb'></td>");
                        currentRow.append("<td class='checkbox-cell right-cb'></td>");
                        i++;
                        continue;
                    }

                    // Found a definition start
                    if (!currentRow.select("td.FrWrd").isEmpty()) {
                        List<Element> block = new ArrayList<>();
                        block.add(currentRow);
                        int j = i + 1;
                        while (j < rows.size()) {
                            Element nextRow = rows.get(j);
                            if (!nextRow.select("td.FrWrd").isEmpty()
                                    || nextRow.hasClass("wrtopsection")
                                    || nextRow.hasClass("langHeader")) {
                                break;
                            }
                            block.add(nextRow);
                            j++;
                        }

                        processExampleBlock(block, word, blockId++);
                        i = j;
                    } else {
                        currentRow.prepend("<td class='checkbox-cell left-cb'></td>");
                        currentRow.append("<td class='checkbox-cell right-cb'></td>");
                        i++;
                    }
                }
            }

            String langPair = lastLearningLanguage.getIsoCode() + lastNativeLanguage.getIsoCode();
            String sourceUrl = "https://www.wordreference.com/" + langPair + "/" + Uri.encode(word);
            String headerHtml = String.format(
                    "<h2>%s (%s) <a href='%s' class='wr-link' target='_blank'>WR</a></h2>",
                    word, lastLearningLanguage.getDisplayName(), sourceUrl);

            String finalHtml = buildHtmlPage(virtualDictionaryWarning + headerHtml + article.html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (RuntimeException e) {
            Timber.e(e, "Parsing error for %s", word);
            listener.onError("Parsing/processing error: " + e.getMessage(), e);
        }
    }

    private void processExampleBlock(List<Element> block, String headword, int blockId) {
        String entryId = "entry-" + blockId;
        block.get(0).attr("id", entryId);

        List<Element> exampleRows = new ArrayList<>();
        for (Element row : block) {
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
        boolean firstRadioSet = false;
        for (Element row : block) {
            row.attr("data-block-id", entryId);
            row.prepend("<td class='checkbox-cell left-cb'></td>");
            row.append("<td class='checkbox-cell right-cb'></td>");

            Element leftCell = row.selectFirst(".left-cb");
            Element rightCell = row.selectFirst(".right-cb");

            if (!row.select("td.ToWrd").isEmpty()
                    && !block.get(0).select("td.FrWrd em.POS2").isEmpty()) {
                // Translation row - Sense selection checkbox on the RIGHT
                // Only if parent block has POS2
                rightCell.append(
                        "<input type='checkbox' class='sense-checkbox' onchange='const bId = this.closest(\"tr\").getAttribute(\"data-block-id\"); const blockRows = document.querySelectorAll(\"tr[data-block-id=\" + bId + \"]\"); blockRows.forEach(r => r.classList.remove(\"selected\")); blockRows.forEach(r => { const cb = r.querySelector(\".sense-checkbox\"); if (cb && cb !== this) cb.checked = false; }); if (this.checked) { blockRows.forEach(r => r.classList.add(\"selected\")); }'>");
            }

            if (valid && !row.select("td.FrEx").isEmpty()) {
                // Example selection - ONLY if block is valid
                leftCell.append("<input type='checkbox' class='example-checkbox'>");
                leftCell.append("<input type='radio' class='example-radio' name='radio-" + entryId + "'"
                        + (!firstRadioSet ? " checked" : "") + ">");
                firstRadioSet = true;
            }

            if (valid && !row.select("td.FrEx").isEmpty() && !entryHeadword.equalsIgnoreCase(headword)) {
                String alert =
                        "<span title='Entry word mismatch: " + entryHeadword + "' style='cursor:help;'>⚠️</span> ";
                if (row.childrenSize() > 1) {
                    row.child(1).prepend(alert);
                }
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
            <div id='filter-toggle-container' style='margin-bottom: 15px; background: #f0f4f8; padding: 10px; border-radius: 4px; display: flex; align-items: center; font-size: 0.9em; border: 1px solid #d1d9e0;'>
                <input type='checkbox' id='filter-toggle' checked onchange='document.body.classList.toggle("filter-active", this.checked)' style='margin-right: 10px; transform: scale(1.2);'>
                <label for='filter-toggle' style='font-weight: bold; color: #24292f;'>Hide entries without valid examples</label>
            </div>
            """;

        String css =
                """
                body { font-family: sans-serif; padding: 12px; line-height: 1.4; color: #333; }
                h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; display: flex; align-items: center; }
                .wr-link { text-decoration: none; color: #36c; margin-left: 12px; font-size: 0.6em; font-weight: bold; border: 1px solid #36c; padding: 0 6px; border-radius: 4px; background: #f0f7ff; vertical-align: middle; }
                body.filter-active tr.no-examples.main-row td:nth-child(n+3) { font-size: 0; }
                body.filter-active tr.no-examples:not(.main-row) { display: none; }
                .virtual-dictionary { margin-bottom: 15px; background: #d1d4b0; padding: 10px; border-radius: 4px; display: flex; align-items: left; border: 1px solid #cc2f21; }
                .WRD { width: 100%; border-collapse: collapse; margin-bottom: 1em; font-size: 0.9em; }
                .WRD tr.odd { background-color: #f6f6f6; }
                .WRD tr.even { background-color: #ffffff; }
                .WRD tr.selected { background-color: #f8f9fa !important; }
                .WRD td { padding: 6px 4px; vertical-align: top; }
                .WRD .Fr, .WRD .FrEx, .WRD .Fr3 { font-weight: bold; color: #000; }
                .WRD .To, .WRD .ToEx, .WRD .To3 { color: #444; }
                .ex { font-style: italic; color: #666; display: block; margin-top: 2px; }
                .example-checkbox, .example-radio, .sense-checkbox { transform: scale(1.2); margin: 0; }
                .checkbox-cell { width: 30px; text-align: center; vertical-align: middle !important; }
                .DS { color: #888; font-size: 0.85em; font-style: italic; }
                .wrtopsection { background-color: #36c; color: #fff; font-weight: bold; padding: 4px 8px !important; }

                /* Mode Toggling */
                body.mode-examples .sense-checkbox, body.mode-examples .example-radio { display: none !important; }
                body.mode-definitions .example-checkbox { display: none !important; }
                body.mode-definitions #filter-toggle-container { display: none !important; }
                body.mode-definitions tr.no-examples.main-row td:nth-child(n+3) { font-size: inherit; }
                body.mode-definitions tr.no-examples:not(.main-row) { display: table-row; }
                body.mode-definitions tr .example-radio { visibility: hidden; }
                body.mode-definitions tr.selected .example-radio { visibility: visible; }
                """;

        return """
                    <html><head><meta name='viewport' content='width=device-width, initial-scale=1'><script>function setMode(mode) {
                        document.body.classList.remove('mode-examples', 'mode-definitions');
                        document.body.classList.add('mode-' + mode);
                    }</script><style>
                """
                + css
                + "</style></head><body data-word='" + word + "' class='filter-active'>" + filterHtml + bodyContent
                + "</body></html>";
    }
}
