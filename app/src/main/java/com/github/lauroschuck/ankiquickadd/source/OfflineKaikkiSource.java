package com.github.lauroschuck.ankiquickadd.source;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.lauroschuck.ankiquickadd.model.Language;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OfflineKaikkiSource implements DictionarySource {
    private static final String TAG = "OfflineKaikkiSource";
    private final Context context;
    private final Handlebars handlebars = new Handlebars();
    private Template template;

    public OfflineKaikkiSource(Context context) {
        this.context = context;
        try {
            this.template = handlebars.compileInline("""
                <html>
                <head>
                    <style>
                        body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                        h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; }
                        h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; }
                        ol { padding-left: 1.5em; }
                        li.definition { margin-bottom: 0.8em; }
                        .gloss { margin-bottom: 0.3em; }
                        dl { margin-top: 0.5em; margin-bottom: 0.5em; }
                        .h-usage-example { font-style: italic; display: block; margin-top: 0.5em; }
                        .h-usage-example-translation { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; }
                        a { color: #36c; text-decoration: none; }
                        .example-checkbox { margin-right: 8px; vertical-align: middle; }
                        .relation-group { margin: 4px 0; font-size: 0.9em; }
                        .relation-label { cursor: pointer; background: #eee; padding: 2px 6px; border-radius: 3px; margin-right: 8px; font-weight: bold; color: #36c; }
                        .relation-label:hover { background: #ddd; }
                        .pronunciation-box { background: #f0f7ff; padding: 10px; border-radius: 4px; margin-bottom: 1em; border-left: 4px solid #36c; }
                        .pronunciation-item { margin-bottom: 4px; display: flex; align-items: center; }
                        .play-button { text-decoration: none; background: #36c; color: white; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 12px; margin-right: 8px; }
                        .pron-desc { font-size: 0.9em; color: #555; }
                        .no-desc { color: #999; font-style: italic; }
                    </style>
                </head>
                <body data-word="{{word}}">
                    <h2>{{word}}</h2>
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
                            <li class='definition'>
                                {{#each glosses}}
                                <div class='gloss'>{{{this}}}</div>
                                {{/each}}
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
                                        <input type='checkbox' class='example-checkbox'>
                                        <span lang='sv'>{{{sourceText}}}</span>
                                        <div class='h-usage-example-translation'>{{{targetText}}}</div>
                                    </dd>
                                    {{/each}}
                                </dl>
                                {{/if}}
                            </li>
                            {{/each}}
                        </ol>
                    </div>
                    {{/each}}
                </body>
                </html>
                """);
        } catch (IOException e) {
            Log.e(TAG, "Handlebars compilation failed", e);
        }
    }

    @Override
    public void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener) {
        String dbName = String.format("wiktionary_kaikki_%s-%s.db", 
                sourceLanguage.getIsoCode().toLowerCase(), 
                targetLanguage.getIsoCode().toLowerCase());
        
        copyDatabaseIfNeeded(dbName);

        File dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            listener.onError("Offline database not found: " + dbName);
            return;
        }

        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            Map<String, Object> data = new HashMap<>();
            data.put("word", word);

            // 1. Fetch Pronunciations
            List<Map<String, String>> pronunciations = new ArrayList<>();
            String pronQuery = "SELECT audio_url, description FROM pronunciations p JOIN headwords h ON p.headword_id = h.id WHERE h.headword = ? COLLATE NOCASE";
            try (Cursor cursor = db.rawQuery(pronQuery, new String[]{word})) {
                while (cursor.moveToNext()) {
                    Map<String, String> pron = new HashMap<>();
                    String url = cursor.getString(0);
                    String desc = cursor.getString(1);
                    pron.put("encodedUrl", URLEncoder.encode(url, "UTF-8"));
                    pron.put("description", desc != null && !desc.isEmpty() ? desc : "<span class='no-desc'>No description</span>");
                    pronunciations.add(pron);
                }
            }
            data.put("pronunciations", pronunciations);

            // 2. Fetch Lexical Entries and nest everything
            List<Map<String, Object>> posBlocks = new ArrayList<>();
            String mainQuery = "SELECT le.id, le.lexical_category FROM lexical_entries le JOIN headwords h ON le.headword_id = h.id WHERE h.headword = ? COLLATE NOCASE ORDER BY le.lexical_category, le.sense_index";
            
            try (Cursor cursor = db.rawQuery(mainQuery, new String[]{word})) {
                Map<String, List<Map<String, Object>>> posMap = new LinkedHashMap<>();
                while (cursor.moveToNext()) {
                    long entryId = cursor.getLong(0);
                    String pos = cursor.getString(1);

                    Map<String, Object> sense = new HashMap<>();
                    
                    // Glosses
                    List<String> glosses = new ArrayList<>();
                    String glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
                    try (Cursor glossCursor = db.rawQuery(glossQuery, new String[]{String.valueOf(entryId)})) {
                        while (glossCursor.moveToNext()) {
                            glosses.add(applyLinks(glossCursor.getString(0), entryId, db));
                        }
                    }
                    sense.put("glosses", glosses);

                    // Relations
                    List<Map<String, Object>> relations = new ArrayList<>();
                    String[] relTypes = {"S", "A"};
                    String[] relLabels = {"Synonyms", "Antonyms"};
                    for (int i = 0; i < relTypes.length; i++) {
                        List<String> words = new ArrayList<>();
                        try (Cursor relCursor = db.rawQuery("SELECT word FROM relations WHERE lexical_entry_id = ? AND type = ?", new String[]{String.valueOf(entryId), relTypes[i]})) {
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
                    List<Map<String, String>> examples = new ArrayList<>();
                    try (Cursor exCursor = db.rawQuery("SELECT id, source_text, target_text FROM examples WHERE lexical_entry_id = ?", new String[]{String.valueOf(entryId)})) {
                        while (exCursor.moveToNext()) {
                            Map<String, String> ex = new HashMap<>();
                            long exId = exCursor.getLong(0);
                            ex.put("sourceText", applyBolding(exCursor.getString(1), exId, "S", db));
                            ex.put("targetText", applyBolding(exCursor.getString(2), exId, "T", db));
                            examples.add(ex);
                        }
                    }
                    sense.put("examples", examples);

                    posMap.computeIfAbsent(pos, k -> new ArrayList<>()).add(sense);
                }

                for (Map.Entry<String, List<Map<String, Object>>> entry : posMap.entrySet()) {
                    Map<String, Object> block = new HashMap<>();
                    block.put("pos", entry.getKey());
                    block.put("senses", entry.getValue());
                    posBlocks.add(block);
                }
            }
            data.put("posBlocks", posBlocks);

            listener.onSuccess(template.apply(data), word);
            
        } catch (Exception e) {
            Log.e(TAG, "Template processing failed", e);
            listener.onError("Offline DB error: " + e.getMessage());
        }
    }

    @Override
    public void fetchMore(String word, Language sourceLanguage, Language targetLanguage, int page, OnResultListener listener) {
    }

    private String applyLinks(String text, long entryId, SQLiteDatabase db) {
        String query = "SELECT sl.word, h.headword FROM sense_links sl JOIN headwords h ON sl.target_headword_id = h.id WHERE sl.lexical_entry_id = ?";
        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(entryId)})) {
            while (cursor.moveToNext()) {
                String linkText = cursor.getString(0);
                String targetWord = cursor.getString(1);
                text = text.replace(linkText, "<a href='app://fetch/" + targetWord + "'>" + linkText + "</a>");
            }
        }
        return text;
    }

    private String applyBolding(String text, long exampleId, String isTranslation, SQLiteDatabase db) {
        List<int[]> offsets = new ArrayList<>();
        String query = "SELECT start_index, end_index FROM bold_offsets WHERE example_id = ? AND is_translation = ?";
        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(exampleId), isTranslation})) {
            while (cursor.moveToNext()) {
                offsets.add(new int[]{cursor.getInt(0), cursor.getInt(1)});
            }
        }
        if (offsets.isEmpty()) return text;
        offsets.sort((a, b) -> Integer.compare(b[0], a[0]));
        StringBuilder sb = new StringBuilder(text);
        for (int[] range : offsets) {
            int start = range[0];
            int end = range[1];
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
                    const cards = [];
                    const headword = document.body.getAttribute('data-word');
                    document.querySelectorAll('.h-usage-example').forEach(example => {
                        const cb = example.querySelector('.example-checkbox');
                        if (cb && cb.checked) {
                            const sourceText = example.querySelector('span[lang]').innerHTML;
                            const targetText = example.querySelector('.h-usage-example-translation').innerHTML;
                            let definition = '';
                            let parent = example.parentElement;
                            while (parent && !parent.classList.contains('definition')) parent = parent.parentElement;
                            if (parent) {
                                const glossEl = parent.querySelector('.gloss');
                                if (glossEl) definition = glossEl.innerText;
                            }
                            let category = '';
                            let posBlock = example.closest('.pos-block');
                            if (posBlock) {
                                const h3 = posBlock.querySelector('h3');
                                if (h3) category = h3.innerText;
                            }
                            cards.push({ headword, sourceText, targetText, definition, lexicalCategory: category });
                        }
                    });
                    Android.processSelectedCards(JSON.stringify(cards));
                })();
                """;
    }

    private void copyDatabaseIfNeeded(String dbName) {
        java.io.File dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            try {
                dbFile.getParentFile().mkdirs();
                try (InputStream is = context.getAssets().open(dbName);
                     OutputStream os = new FileOutputStream(dbFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
                }
            } catch (IOException e) {
                Log.e(TAG, "Database copy failed", e);
            }
        }
    }
}
