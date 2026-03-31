package com.github.lauroschuck.ankiquickadd.source;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.lauroschuck.ankiquickadd.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.DictionaryNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

public class OfflineKaikkiSource implements DictionarySource {
    private static final String TAG = "OfflineKaikkiSource";
    private final Handlebars handlebars = new Handlebars();
    private Context context;
    private Template template;
    private Language lastLearningLanguage;
    private Language lastNativeLanguage;

    public OfflineKaikkiSource() {
        try {
            this.template = handlebars.compileInline(
                    """
                <html>
                <head>
                    <style>
                        body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; transition: background 0.3s; }
                        h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; display: flex; align-items: center; }
                        h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; }
                        ol { padding-left: 1.5em; }
                        li.definition { margin-bottom: 0.8em; position: relative; }
                        .gloss { margin-bottom: 0.3em; }
                        dl { margin-top: 0.5em; margin-bottom: 0.5em; }
                        .h-usage-example { font-style: italic; display: block; margin-top: 0.5em; }
                        .h-usage-example-translation { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; }
                        a { color: #36c; text-decoration: none; }

                        .example-checkbox, .sense-checkbox, .example-radio { margin-right: 8px; vertical-align: middle; }

                        /* Mode Toggling */
                        body.mode-examples .sense-checkbox, body.mode-examples .example-radio { display: none !important; }
                        body.mode-definitions .example-checkbox { display: none !important; }

                        /* Mode-specific gloss layout */
                        body.mode-examples .gloss { display: block; }
                        body.mode-definitions .gloss { display: inline-block; width: 90%; vertical-align: top; }

                        /* Definitions mode specifics */
                        body.mode-definitions .h-usage-example .example-radio { visibility: hidden; }
                        body.mode-definitions li.definition.selected .h-usage-example .example-radio { visibility: visible; }
                        body.mode-definitions li.definition.selected { background-color: #f8f9fa; border-radius: 4px; }

                        .relation-group { margin: 4px 0; font-size: 0.9em; }
                        .relation-label { cursor: pointer; background: #eee; padding: 2px 6px; border-radius: 3px; margin-right: 8px; font-weight: bold; color: #36c; }
                        .relation-label:hover { background: #ddd; }
                        .pronunciation-box { background: #f0f7ff; padding: 10px; border-radius: 4px; margin-bottom: 1em; border-left: 4px solid #36c; }
                        .pronunciation-item { margin-bottom: 4px; display: flex; align-items: center; }
                        .play-button { text-decoration: none; background: #36c; color: white; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 12px; margin-right: 8px; }
                        .pron-desc { font-size: 0.9em; color: #555; }
                        .no-desc { color: #999; font-style: italic; }

                        .wiktionary-link { text-decoration: none; color: #36c; margin-left: 12px; font-size: 0.6em; font-weight: bold; border: 1px solid #36c; padding: 0 6px; border-radius: 4px; background: #f0f7ff; vertical-align: middle; }
                        .did-you-mean { margin-bottom: 1em; font-size: 0.9em; color: #555; font-style: italic; }
                        .did-you-mean a { font-style: normal; font-weight: bold; margin-right: 8px; }

                        .footer { margin-top: 30px; padding-top: 15px; border-top: 1px solid #eee; text-align: center; margin-bottom: 20px; }
                        .footer a { color: #36c; font-size: 0.95em; }
                    </style>
                    <script>
                        function setMode(mode) {
                            document.body.className = 'mode-' + mode;
                        }
                        function toggleSense(cb) {
                            cb.closest('li.definition').classList.toggle('selected', cb.checked);
                            if (window.Android) {
                                var count = document.querySelectorAll('.sense-checkbox:checked').length;
                                Android.updateSelectedCount(count);
                            }
                        }
                    </script>
                </head>
                <body data-word="{{word}}" class="mode-examples">
                    <h2>
                        {{word}} ({{langName}})
                        {{#if wiktionaryUrl}}
                        <a href="{{wiktionaryUrl}}" class="wiktionary-link" target="_blank">W</a>
                        {{/if}}
                    </h2>

                    {{#if variations}}
                    <div class="did-you-mean">
                        Did you mean:
                        {{#each variations}}
                        <a href="app://fetch/{{this}}">{{this}}</a>
                        {{/each}}
                    </div>
                    {{/if}}

                    {{#if pronunciations}}
                    <div class='pronunciation-box'>
                        {{#each pronunciations}}
                        <div class='pronunciation-item'>
                            <a class='play-button' href='app://play/{{encodedUrl}}'>&#9658;</a>
                            <span class='pron-desc'>{{{description}}}</span>
                        </div>
                        {{/each}}
                    </div>
                    {{/if}}
                    {{#each posBlocks}}
                    <div class='pos-block'>
                        <h3>{{pos}}</h3>
                        <ol>
                            {{#each senses}}
                            <li class='definition' id='sense-{{entryId}}'>
                                <input type='checkbox' class='sense-checkbox' id='chk-sense-{{entryId}}' onchange='toggleSense(this)'>
                                <div class='gloss'>
                                    {{#each glosses}}
                                    <div>{{{this}}}</div>
                                    {{/each}}
                                </div>
                                {{#each relations}}
                                <div class='relation-group'>
                                    <span class='relation-label' onclick='var el = this.nextElementSibling; el.style.display = el.style.display === "none" ? "inline" : "none"'>{{label}}</span>
                                    <span class='relation-links' style='display:none'>
                                        {{#each words}}
                                        {{#unless @first}}, {{/unless}}<a href='app://fetch/{{this}}'>{{this}}</a>
                                        {{/each}}
                                    </span>
                                </div>
                                {{/each}}
                                {{#if examples}}
                                <dl>
                                    {{#each examples}}
                                    <dd class='h-usage-example'>
                                        <input type='checkbox' class='example-checkbox' id='example-{{id}}'>
                                        <input type='radio' class='example-radio' name='radio-sense-{{../entryId}}' value='{{id}}' {{#if @first}}checked{{/if}}>
                                        <span lang='sv'>{{{learningText}}}</span>
                                        <div class='h-usage-example-translation'>{{{nativeText}}}</div>
                                    </dd>
                                    {{/each}}
                                </dl>
                                {{/if}}
                            </li>
                            {{/each}}
                        </ol>
                    </div>
                    {{/each}}

                    {{#if wiktionaryUrl}}
                    <div class="footer">
                        <a href="{{wiktionaryUrl}}" target="_blank">View this word on Wiktionary</a>
                    </div>
                    {{/if}}
                </body>
                </html>
                """);
        } catch (IOException e) {
            Log.e(TAG, "Handlebars compilation failed", e);
        }
    }

    @Override
    public String getName() {
        return "Wiktionary";
    }

    @Override
    public void setContext(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void fetch(
            @NonNull String word,
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            @NonNull OnResultListener listener) {
        this.lastLearningLanguage = learningLanguage;
        this.lastNativeLanguage = nativeLanguage;

        var dbName = String.format(
                "wiktionary_kaikki_%s-%s.db",
                learningLanguage.getIsoCode().toLowerCase(),
                nativeLanguage.getIsoCode().toLowerCase());

        Log.d(TAG, "Fetching word: " + word + " from DB: " + dbName);
        copyDatabaseIfNeeded(dbName);

        var dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            Log.e(TAG, "Database file not found: " + dbFile.getAbsolutePath());
            listener.onError("Offline database not found: " + dbName);
            return;
        }

        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            var data = new HashMap<String, Object>();
            data.put("word", word);

            // 0. Fetch language name for Wiktionary anchor and construct URL
            var langName = getLanguageName(db, learningLanguage);
            data.put("langName", langName);
            data.put("wiktionaryUrl", assembleWiktionaryLink(word, nativeLanguage, langName));

            // 1. Fetch Pronunciations
            var pronunciations = new ArrayList<Map<String, String>>();
            var pronQuery =
                    "SELECT audio_url, description FROM pronunciations p JOIN headwords h ON p.headword_id = h.id WHERE h.headword = ? COLLATE BINARY";
            try (var cursor = db.rawQuery(pronQuery, new String[] {word})) {
                while (cursor.moveToNext()) {
                    Map<String, String> pron = new HashMap<>();
                    String url = cursor.getString(0);
                    String desc = cursor.getString(1);
                    pron.put("encodedUrl", URLEncoder.encode(url, "UTF-8"));
                    pron.put(
                            "description",
                            desc != null && !desc.isEmpty() ? desc : "<span class='no-desc'>No description</span>");
                    pronunciations.add(pron);
                }
            }
            data.put("pronunciations", pronunciations);
            Log.d(TAG, "Found " + pronunciations.size() + " pronunciations");

            // 2. Fetch variations (other casings)
            var variations = new ArrayList<String>();
            String varQuery =
                    "SELECT headword FROM headwords WHERE headword = ? COLLATE NOCASE AND headword != ? COLLATE BINARY";
            try (Cursor cursor = db.rawQuery(varQuery, new String[] {word, word})) {
                while (cursor.moveToNext()) {
                    variations.add(cursor.getString(0));
                }
            }
            if (!variations.isEmpty()) {
                data.put("variations", variations);
            }

            // 3. Fetch Lexical Entries and nest everything
            var posBlocks = new ArrayList<Map<String, Object>>();
            String mainQuery =
                    "SELECT le.id, le.lexical_category FROM lexical_entries le JOIN headwords h ON le.headword_id = h.id WHERE h.headword = ? COLLATE BINARY ORDER BY le.lexical_category, le.sense_index";

            try (Cursor cursor = db.rawQuery(mainQuery, new String[] {word})) {
                var posMap = new LinkedHashMap<String, List<Map<String, Object>>>();
                while (cursor.moveToNext()) {
                    long entryId = cursor.getLong(0);
                    String pos = cursor.getString(1);

                    var sense = new HashMap<String, Object>();
                    sense.put("entryId", entryId);

                    // Glosses
                    var glosses = new ArrayList<String>();
                    var glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
                    try (Cursor glossCursor = db.rawQuery(glossQuery, new String[] {String.valueOf(entryId)})) {
                        while (glossCursor.moveToNext()) {
                            glosses.add(applyLinks(glossCursor.getString(0), entryId, db));
                        }
                    }
                    sense.put("glosses", glosses);

                    // Relations
                    var relations = new ArrayList<Map<String, Object>>();
                    var relTypes = new String[] {"S", "A"};
                    var relLabels = new String[] {"Synonyms", "Antonyms"};
                    for (int i = 0; i < relTypes.length; i++) {
                        var words = new ArrayList<String>();
                        try (Cursor relCursor = db.rawQuery(
                                "SELECT word FROM relations WHERE lexical_entry_id = ? AND type = ?",
                                new String[] {String.valueOf(entryId), relTypes[i]})) {
                            while (relCursor.moveToNext()) words.add(relCursor.getString(0));
                        }
                        if (!words.isEmpty()) {
                            Map<String, Object> rel = new HashMap<>();
                            rel.put("label", relLabels[i]);
                            rel.put("words", words);
                            relations.add(rel);
                        }
                    }
                    sense.put("relations", relations);

                    // Examples
                    var examples = new ArrayList<Map<String, Object>>();
                    try (var exCursor = db.rawQuery(
                            "SELECT id, learning_text, native_text FROM examples WHERE lexical_entry_id = ?",
                            new String[] {String.valueOf(entryId)})) {
                        while (exCursor.moveToNext()) {
                            var ex = new HashMap<String, Object>();
                            var exId = exCursor.getLong(0);
                            ex.put("id", exId);
                            ex.put("learningText", applyBolding(exCursor.getString(1), exId, "L", db));
                            ex.put("nativeText", applyBolding(exCursor.getString(2), exId, "N", db));
                            examples.add(ex);
                        }
                    }
                    sense.put("examples", examples);

                    posMap.computeIfAbsent(pos, k -> new ArrayList<>()).add(sense);
                }

                for (var entry : posMap.entrySet()) {
                    var block = new HashMap<String, Object>();
                    block.put("pos", entry.getKey());
                    block.put("senses", entry.getValue());
                    posBlocks.add(block);
                }
            }
            data.put("posBlocks", posBlocks);
            Log.d(TAG, "Found " + posBlocks.size() + " POS blocks");

            if (posBlocks.isEmpty()) {
                // If word not found exactly, but we have variations, show them in a special "not found" page
                if (!variations.isEmpty()) {
                    var html = template.apply(data);
                    listener.onSuccess(html, word);
                } else {
                    listener.onError("Word not found in offline database: " + word);
                }
                return;
            }

            var html = template.apply(data);
            Log.d(TAG, "Generated HTML length: " + html.length());
            listener.onSuccess(html, word);

        } catch (Exception e) {
            Log.e(TAG, "Template processing failed", e);
            listener.onError("Offline DB error: " + e.getMessage());
        }
    }

    private String getLanguageName(SQLiteDatabase db, Language language) {
        try (var langCursor =
                db.rawQuery("SELECT name FROM languages WHERE iso = ?", new String[] {language.getIsoCode()})) {
            if (langCursor.moveToFirst()) {
                return langCursor.getString(0);
            }
        }
        throw new IllegalStateException("No language name found for " + language.getIsoCode());
    }

    private static String assembleWiktionaryLink(
            @NonNull String headword, @NonNull Language nativeLanguage, @NonNull String anchor) {
        try {
            var encodedWord = URLEncoder.encode(headword, "UTF-8");
            var encodedAnchor = URLEncoder.encode(anchor.replace(" ", "_"), "UTF-8");
            return String.format(
                    "https://%s.wiktionary.org/wiki/%s#%s",
                    nativeLanguage.getIsoCode().toLowerCase(), encodedWord, encodedAnchor);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Failed to construct link for %s in %s-%s", headword, anchor, nativeLanguage), e);
        }
    }

    private String applyLinks(String text, long entryId, SQLiteDatabase db) {
        var query =
                "SELECT sl.word, h.headword FROM sense_links sl JOIN headwords h ON sl.native_headword_id = h.id WHERE sl.lexical_entry_id = ?";
        try (var cursor = db.rawQuery(query, new String[] {String.valueOf(entryId)})) {
            while (cursor.moveToNext()) {
                String linkText = cursor.getString(0);
                String nativeWord = cursor.getString(1);
                text = text.replace(linkText, "<a href='app://fetch/" + nativeWord + "'>" + linkText + "</a>");
            }
        }
        return text;
    }

    private String applyBolding(String text, long exampleId, String type, SQLiteDatabase db) {
        var offsets = new ArrayList<int[]>();
        var query = "SELECT start_index, end_index FROM bold_offsets WHERE example_id = ? AND text_type = ?";
        try (var cursor = db.rawQuery(query, new String[] {String.valueOf(exampleId), type})) {
            while (cursor.moveToNext()) {
                offsets.add(new int[] {cursor.getInt(0), cursor.getInt(1)});
            }
        }
        if (offsets.isEmpty()) {
            return text;
        }
        offsets.sort((a, b) -> Integer.compare(b[0], a[0]));
        var sb = new StringBuilder(text);
        for (var range : offsets) {
            var start = range[0];
            var end = range[1];
            if (end <= sb.length() && start >= 0 && start < end) {
                sb.insert(end, "</b>").insert(start, "<b>");
            }
        }
        return sb.toString();
    }

    @Override
    public String getExtractionJs() {
        return """
                (() => {
                    const isDefinitions = document.body.classList.contains('mode-definitions');
                    if (isDefinitions) {
                        const entries = [];
                        document.querySelectorAll('.sense-checkbox:checked').forEach(cb => {
                            const entryId = cb.id.replace('chk-sense-', '');
                            const radio = document.querySelector('input[name="radio-sense-' + entryId + '"]:checked');
                            entries.push({
                                entryId: entryId,
                                exampleId: radio ? radio.value : null
                            });
                        });
                        Android.processSelectedCards(JSON.stringify({ mode: 'definitions', entries: entries }));
                    } else {
                        const selectedIds = [];
                        document.querySelectorAll('.example-checkbox:checked').forEach(cb => {
                            const id = cb.id.replace('example-', '');
                            selectedIds.push(id);
                        });
                        Android.processSelectedCards(JSON.stringify({ mode: 'examples', examples: selectedIds }));
                    }
                })();
                """;
    }

    @Override
    public SelectedCards<? extends AbstractAnkiNote.Input> getCardsFromSelection(@NonNull String json) {
        try {
            var obj = new Gson().fromJson(json, JsonObject.class);
            var mode = obj.has("mode") ? obj.get("mode").getAsString() : "examples";

            if (lastLearningLanguage == null || lastNativeLanguage == null) {
                throw new IllegalArgumentException(
                        String.format("No language pair: %s-%s", lastLearningLanguage, lastNativeLanguage));
            }

            var dbName = String.format(
                    "wiktionary_kaikki_%s-%s.db",
                    lastLearningLanguage.getIsoCode().toLowerCase(),
                    lastNativeLanguage.getIsoCode().toLowerCase());

            var dbFile = context.getDatabasePath(dbName);
            if (!dbFile.exists()) {
                throw new IllegalArgumentException("Offline database not found: " + dbName);
            }

            try (var db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                if (mode.equals("examples")) {
                    var exampleIds = obj.getAsJsonArray("examples");
                    var cards = new ArrayList<TextNote.Input>();
                    String headword = null;
                    for (var idElem : exampleIds) {
                        var card = fetchCardForExample(db, idElem.getAsLong());
                        headword = card.headword();
                        cards.add(card);
                    }
                    var audioUrl = fetchAudioUrl(db, headword);
                    var sourceUrl = assembleWiktionaryLink(
                            headword, lastNativeLanguage, getLanguageName(db, lastLearningLanguage));
                    return new SelectedTextCards(lastLearningLanguage, lastNativeLanguage, audioUrl, sourceUrl, cards);
                } else {
                    var entries = obj.getAsJsonArray("entries");
                    var entryMap = new LinkedHashMap<Long, Long>();
                    for (var entryElem : entries) {
                        var entry = entryElem.getAsJsonObject();
                        var entryId = entry.get("entryId").getAsLong();
                        var exIdElem = entry.get("exampleId");
                        var exampleId = (exIdElem == null || exIdElem.isJsonNull()) ? null : exIdElem.getAsLong();
                        entryMap.put(entryId, exampleId);
                    }
                    var cards = fetchCardForDefinition(db, entryMap);
                    var headword = cards.stream()
                            .findFirst()
                            .map(DictionaryNote.Input::headword)
                            .orElseThrow();
                    var audioUrl = fetchAudioUrl(db, headword);
                    var sourceUrl = assembleWiktionaryLink(
                            headword, lastNativeLanguage, getLanguageName(db, lastLearningLanguage));
                    return new SelectedDictionaryCards(
                            lastLearningLanguage, lastNativeLanguage, audioUrl, sourceUrl, cards);
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Error generating cards from selection", e);
            throw e;
        }
    }

    private TextNote.Input fetchCardForExample(SQLiteDatabase db, long exId) {
        var query = "SELECT e.learning_text, e.native_text, le.lexical_category, h.headword, e.lexical_entry_id "
                + "FROM examples e "
                + "JOIN lexical_entries le ON e.lexical_entry_id = le.id "
                + "JOIN headwords h ON le.headword_id = h.id "
                + "WHERE e.id = ?";

        try (var cursor = db.rawQuery(query, new String[] {String.valueOf(exId)})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("Example not found: " + exId);
            }

            var learningTextRaw = cursor.getString(0);
            var nativeTextRaw = cursor.getString(1);
            var lexicalCategory = cursor.getString(2);
            var headword = cursor.getString(3);
            var entryId = cursor.getLong(4);

            var learningText = applyBolding(learningTextRaw, exId, "L", db);
            var nativeText = applyBolding(nativeTextRaw, exId, "N", db);
            var glosses = fetchGlosses(db, entryId);

            return new TextNote.Input(headword, learningText, nativeText, glosses, lexicalCategory);
        }
    }

    private List<DictionaryNote.Input> fetchCardForDefinition(SQLiteDatabase db, Map<Long, Long> entryMap) {
        var examples = entryMap.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
        var query = String.format(
                """
            SELECT le.id, h.headword, le.lexical_category, e.id, e.learning_text, e.native_text
            FROM headwords h
            JOIN lexical_entries le ON le.headword_id = h.id
            LEFT JOIN examples e ON e.lexical_entry_id = le.id
            WHERE le.id IN %s
                AND (%s e.id IS NULL)
            ORDER BY le.lexical_category, le.sense_index, e.id
            """,
                createPlaceholderString(entryMap.size()),
                examples.isEmpty() ? "" : "e.id IN " + createPlaceholderString(examples.size()) + " OR");

        var arguments = Stream.concat(entryMap.keySet().stream(), examples.stream())
                .map(Object::toString)
                .collect(Collectors.toList())
                .toArray(new String[] {});
        Log.d(TAG, String.format("Query:\n%sArguments: %s", query, Arrays.toString(arguments)));
        var flattenedCards = new ArrayList<DictionaryNote.Input>();
        try (var cursor = db.rawQuery(query, arguments)) {
            while (cursor.moveToNext()) {
                var entryId = cursor.getLong(0);
                var headword = cursor.getString(1);
                var lexicalCategory = cursor.getString(2);
                var definitionText = fetchGlosses(db, entryId);
                DictionaryNote.Input.Definition definition;
                if (cursor.isNull(3)) {
                    definition = new DictionaryNote.Input.Definition(definitionText, null, null);
                } else {
                    var exampleId = cursor.getLong(3);
                    var learningText = applyBolding(cursor.getString(4), exampleId, "L", db);
                    var nativeText = applyBolding(cursor.getString(5), exampleId, "N", db);
                    definition = new DictionaryNote.Input.Definition(definitionText, learningText, nativeText);
                }
                var input = new DictionaryNote.Input(headword, lexicalCategory, List.of(definition));
                Log.d(TAG, String.format("Intermediary input: %s", input));
                flattenedCards.add(input);
            }
        }

        return flattenedCards.stream()
                .collect(Collectors.groupingBy(DictionaryNote.Input::lexicalCategory))
                .values()
                .stream()
                .map(inputs -> {
                    var definitions = inputs.stream()
                            .map(input -> input.definitions().get(0))
                            .collect(Collectors.toList());
                    var firstInput = inputs.get(0);
                    var input =
                            new DictionaryNote.Input(firstInput.headword(), firstInput.lexicalCategory(), definitions);
                    Log.d(TAG, String.format("Condensed input: %s", input));
                    return input;
                })
                .collect(Collectors.toList());
    }

    private static String createPlaceholderString(int size) {
        var placeholders = new String[size];
        Arrays.fill(placeholders, "?");
        return "(" + TextUtils.join(",", placeholders) + ")";
    }

    private String fetchAudioUrl(SQLiteDatabase db, String headword) {
        // TODO handle multiple audios
        var audioQuery =
                "SELECT audio_url FROM pronunciations p JOIN headwords h ON p.headword_id = h.id WHERE h.headword = ? LIMIT 1";
        try (var audioCursor = db.rawQuery(audioQuery, new String[] {headword})) {
            if (audioCursor.moveToFirst()) {
                return audioCursor.getString(0);
            }
        }
        return null;
    }

    private String fetchGlosses(SQLiteDatabase db, long entryId) {
        var glosses = new StringBuilder();
        var glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
        try (var glossCursor = db.rawQuery(glossQuery, new String[] {String.valueOf(entryId)})) {
            while (glossCursor.moveToNext()) {
                if (glosses.length() > 0) {
                    glosses.append("<br/>");
                }
                glosses.append(applyLinks(glossCursor.getString(0), entryId, db));
            }
        }
        return glosses.toString();
    }

    private void copyDatabaseIfNeeded(String dbName) {
        var dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            try {
                dbFile.getParentFile().mkdirs();
                try (InputStream is = context.getAssets().open(dbName);
                        OutputStream os = new FileOutputStream(dbFile)) {
                    var buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Database copy failed", e);
            }
        }
    }
}
