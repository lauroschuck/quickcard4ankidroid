package com.github.lauroschuck.ankiquickadd.source;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.github.lauroschuck.ankiquickadd.model.Language;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class OfflineKaikkiSource implements DictionarySource {
    private static final String TAG = "OfflineKaikkiSource";
    private final Context context;

    public OfflineKaikkiSource(Context context) {
        this.context = context;
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
            
            StringBuilder html = new StringBuilder();
            html.append("<h2>").append(word).append("</h2>");

            boolean found = false;

            // 1. Fetch Pronunciations
            String pronQuery = "SELECT audio_url, description FROM pronunciations p " +
                    "JOIN headwords h ON p.headword_id = h.id " +
                    "WHERE h.headword = ? COLLATE NOCASE";
            try (Cursor cursor = db.rawQuery(pronQuery, new String[]{word})) {
                if (cursor.getCount() > 0) {
                    html.append("<div class='pronunciation-box'>");
                    while (cursor.moveToNext()) {
                        String url = cursor.getString(0);
                        String desc = cursor.getString(1);
                        try {
                            String encodedUrl = URLEncoder.encode(url, "UTF-8");
                            html.append("<div class='pronunciation-item'>")
                                .append("<a class='play-button' href='app://play/").append(encodedUrl).append("'>&#9658;</a> ")
                                .append("<span class='pron-desc'>").append(desc != null && !desc.isEmpty() ? desc : "<span class='no-desc'>No description</span>").append("</span>")
                                .append("</div>");
                        } catch (Exception ignored) {}
                    }
                    html.append("</div>");
                }
            }

            // 2. Fetch main lexical entries, merged by category
            String mainQuery = "SELECT le.id, le.lexical_category, le.sense_index FROM lexical_entries le " +
                    "JOIN headwords h ON le.headword_id = h.id " +
                    "WHERE h.headword = ? COLLATE NOCASE " +
                    "ORDER BY le.lexical_category, le.sense_index";
            
            try (Cursor cursor = db.rawQuery(mainQuery, new String[]{word})) {
                String currentPos = null;
                while (cursor.moveToNext()) {
                    found = true;
                    long entryId = cursor.getLong(0);
                    String pos = cursor.getString(1);

                    if (!pos.equals(currentPos)) {
                        if (currentPos != null) html.append("</ol></div>");
                        currentPos = pos;
                        html.append("<div class='pos-block'><h3>").append(pos).append("</h3><ol>");
                    }

                    html.append("<li class='definition'>");
                    
                    // Fetch all glosses for this sense and apply links
                    String glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
                    try (Cursor glossCursor = db.rawQuery(glossQuery, new String[]{String.valueOf(entryId)})) {
                        while (glossCursor.moveToNext()) {
                            String gloss = glossCursor.getString(0);
                            String glossWithLinks = applyLinks(gloss, entryId, db);
                            html.append("<div class='gloss'>").append(glossWithLinks).append("</div>");
                        }
                    }

                    // Fetch Synonyms/Antonyms
                    appendRelations(html, entryId, db);

                    // Fetch examples for this specific sense
                    String exampleQuery = "SELECT id, source_text, target_text FROM examples WHERE lexical_entry_id = ?";
                    try (Cursor exCursor = db.rawQuery(exampleQuery, new String[]{String.valueOf(entryId)})) {
                        if (exCursor.getCount() > 0) {
                            html.append("<dl>");
                            while (exCursor.moveToNext()) {
                                long exId = exCursor.getLong(0);
                                String src = exCursor.getString(1);
                                String trg = exCursor.getString(2);
                                
                                String boldSrc = applyBolding(src, exId, "S", db);
                                String boldTrg = applyBolding(trg, exId, "T", db);

                                html.append("<dd class='h-usage-example'>")
                                    .append("<input type='checkbox' class='example-checkbox'> ")
                                    .append("<span lang='sv'>").append(boldSrc).append("</span>")
                                    .append("<div class='h-usage-example-translation'>").append(boldTrg).append("</div>")
                                    .append("</dd>");
                            }
                            html.append("</dl>");
                        }
                    }
                    html.append("</li>");
                }
                if (currentPos != null) html.append("</ol></div>");
            }

            if (found) {
                listener.onSuccess(wrapHtml(html.toString(), word), word);
            } else {
                listener.onError("Word not found in offline database: " + word);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Offline DB error", e);
            listener.onError("Offline DB error: " + e.getMessage());
        }
    }

    @Override
    public void fetchMore(String word, Language sourceLanguage, Language targetLanguage, int page, OnResultListener listener) {
        // NO OP
    }

    private String applyLinks(String text, long entryId, SQLiteDatabase db) {
        String query = "SELECT sl.word, h.headword FROM sense_links sl " +
                "JOIN headwords h ON sl.target_headword_id = h.id " +
                "WHERE sl.lexical_entry_id = ?";
        
        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(entryId)})) {
            while (cursor.moveToNext()) {
                String linkText = cursor.getString(0);
                String targetWord = cursor.getString(1);
                text = text.replace(linkText, "<a href='app://fetch/" + targetWord + "'>" + linkText + "</a>");
            }
        }
        return text;
    }

    private void appendRelations(StringBuilder html, long entryId, SQLiteDatabase db) {
        String[] types = {"S", "A"}; // S for Synonym, A for Antonym
        String[] labels = {"Synonyms", "Antonyms"};
        
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            String label = labels[i];
            
            String relQuery = "SELECT word FROM relations WHERE lexical_entry_id = ? AND type = ?";
            try (Cursor cursor = db.rawQuery(relQuery, new String[]{String.valueOf(entryId), type})) {
                if (cursor.getCount() > 0) {
                    html.append("<div class='relation-group'>")
                        .append("<span class='relation-label' onclick='var el = this.nextElementSibling; el.style.display = el.style.display === \"none\" ? \"inline\" : \"none\"'>")
                        .append(label).append("</span>")
                        .append("<span class='relation-links' style='display:none'>");
                    
                    boolean first = true;
                    while (cursor.moveToNext()) {
                        if (!first) html.append(", ");
                        String relWord = cursor.getString(0);
                        html.append("<a href='app://fetch/").append(relWord).append("'>").append(relWord).append("</a>");
                        first = false;
                    }
                    html.append("</span></div>");
                }
            }
        }
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
                            // Find nearest definition
                            let definition = '';
                            let parent = example.parentElement;
                            while (parent && !parent.classList.contains('definition')) {
                                parent = parent.parentElement;
                            }
                            if (parent) {
                                const glossEl = parent.querySelector('.gloss');
                                if (glossEl) definition = glossEl.innerText;
                            }
                            // Find category
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

    private String wrapHtml(String content, String word) {
        String css = "body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; } " +
                     "h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; } " +
                     "h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; } " +
                     "ol { padding-left: 1.5em; } " +
                     "li.definition { margin-bottom: 0.8em; } " +
                     ".gloss { margin-bottom: 0.3em; } " +
                     "dl { margin-top: 0.5em; margin-bottom: 0.5em; } " +
                     ".h-usage-example { font-style: italic; display: block; margin-top: 0.5em; } " +
                     ".h-usage-example-translation { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; } " +
                     "a { color: #36c; text-decoration: none; } " +
                     ".example-checkbox { margin-right: 8px; vertical-align: middle; } " +
                     ".relation-group { margin: 4px 0; font-size: 0.9em; } " +
                     ".relation-label { cursor: pointer; background: #eee; padding: 2px 6px; border-radius: 3px; margin-right: 8px; font-weight: bold; color: #36c; } " +
                     ".relation-label:hover { background: #ddd; } " +
                     ".pronunciation-box { background: #f0f7ff; padding: 10px; border-radius: 4px; margin-bottom: 1em; border-left: 4px solid #36c; } " +
                     ".pronunciation-item { margin-bottom: 4px; display: flex; align-items: center; } " +
                     ".play-button { text-decoration: none; background: #36c; color: white; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 12px; margin-right: 8px; } " +
                     ".pron-desc { font-size: 0.9em; color: #555; } " +
                     ".no-desc { color: #999; font-style: italic; }";
        return "<html><head><style>" + css + "</style></head><body data-word='" + word + "'>" + content + "</body></html>";
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
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                }
                Log.d(TAG, "Database copied successfully: " + dbName);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy database from assets: " + dbName, e);
            }
        }
    }
}
