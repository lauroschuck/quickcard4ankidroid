package com.github.lauroschuck.ankiquickadd.source;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.github.lauroschuck.ankiquickadd.model.Language;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class OfflineKaikkiSource implements DictionarySource {
    private static final String TAG = "OfflineKaikkiSource";
    private static final String DB_NAME = "swedish_dict.db";
    private final Context context;

    public OfflineKaikkiSource(Context context) {
        this.context = context;
        copyDatabaseIfNeeded();
    }

    @Override
    public void fetch(String word, Language sourceLanguage, Language targetLanguage, OnResultListener listener) {
        if (sourceLanguage != Language.SWEDISH) {
            listener.onError("Offline mode only supports Swedish for now.");
            return;
        }

        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                context.getDatabasePath(DB_NAME).getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            
            StringBuilder html = new StringBuilder();
            html.append("<h2>").append(word).append("</h2>");

            boolean found = false;

            // 1. Fetch Pronunciations
            String pronQuery = "SELECT audio_url, description FROM pronunciations p " +
                    "JOIN headwords h ON p.headword_id = h.id " +
                    "WHERE h.headword = ? COLLATE NOCASE";
            try (Cursor cursor = db.rawQuery(pronQuery, new String[]{word})) {
                if (cursor.getCount() > 0) {
                    html.append("<div class='pronunciations'><ul>");
                    while (cursor.moveToNext()) {
                        String url = cursor.getString(0);
                        String desc = cursor.getString(1);
                        html.append("<li><a href='").append(url).append("'>Play Audio</a> ").append(desc).append("</li>");
                    }
                    html.append("</ul></div>");
                }
            }

            // 2. Check if it's a non-lemma (inflection)
            String nonLemmaQuery = "SELECT l.headword, nl.description FROM non_lemmas nl " +
                    "JOIN headwords h ON nl.headword_id = h.id " +
                    "JOIN headwords l ON nl.lemma_id = l.id " +
                    "WHERE h.headword = ? COLLATE NOCASE";
            
            try (Cursor cursor = db.rawQuery(nonLemmaQuery, new String[]{word})) {
                while (cursor.moveToNext()) {
                    found = true;
                    String lemma = cursor.getString(0);
                    String description = cursor.getString(1);
                    html.append("<p class='inflection'>")
                        .append(description.replace("\n", "<br>"))
                        .append(" of <a href='app://fetch/").append(lemma).append("'>").append(lemma).append("</a>")
                        .append("</p>");
                }
            }

             // 3. Fetch main lexical entries, merged by category
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

                    // Start a new block if the lexical category changes (Merging etymologies)
                    if (!pos.equals(currentPos)) {
                        if (currentPos != null) {
                            html.append("</ol></div>");
                        }
                        currentPos = pos;
                        html.append("<div class='pos-block'>");
                        html.append("<h3>").append(pos).append("</h3>");
                        html.append("<ol>"); // Start a single numbered list for all senses of this POS
                    }

                    html.append("<li class='definition'>");
                    
                    // Fetch all glosses for this sense
                    String glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
                    try (Cursor glossCursor = db.rawQuery(glossQuery, new String[]{String.valueOf(entryId)})) {
                        while (glossCursor.moveToNext()) {
                            html.append("<div class='gloss'>").append(glossCursor.getString(0)).append("</div>");
                        }
                    }

                    // Fetch examples for this specific sense
                    String exampleQuery = "SELECT id, source_text, target_text FROM examples WHERE lexical_entry_id = ?";
                    try (Cursor exCursor = db.rawQuery(exampleQuery, new String[]{String.valueOf(entryId)})) {
                        if (exCursor.getCount() > 0) {
                            html.append("<dl>");
                            while (exCursor.moveToNext()) {
                                long exId = exCursor.getLong(0);
                                String src = exCursor.getString(1);
                                String trg = exCursor.getString(2);
                                
                                String boldSrc = applyBolding(src, exId, false, db);
                                String boldTrg = applyBolding(trg, exId, true, db);

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
                if (currentPos != null) {
                    html.append("</ol></div>");
                }
            }

            if (found) {
                listener.onSuccess(wrapHtml(html.toString(), word), word);
            } else {
                listener.onError("Word not found in offline database.");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Offline DB error", e);
            listener.onError("Offline DB error: " + e.getMessage());
        }
    }

    private String applyBolding(String text, long exampleId, boolean isTranslation, SQLiteDatabase db) {
        List<int[]> offsets = new ArrayList<>();
        String query = "SELECT start_index, end_index FROM bold_offsets WHERE example_id = ? AND is_translation = ?";
        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(exampleId), isTranslation ? "1" : "0"})) {
            while (cursor.moveToNext()) {
                offsets.add(new int[]{cursor.getInt(0), cursor.getInt(1)});
            }
        }
        
        if (offsets.isEmpty()) return text;
        
        // Sort offsets by start index in descending order to avoid index shifts during tag insertion
        offsets.sort((a, b) -> Integer.compare(b[0], a[0]));
        
        StringBuilder sb = new StringBuilder(text);
        for (int[] range : offsets) {
            int start = range[0];
            int end = range[1];
            
            // Apply tags interactively from end to beginning
            if (end <= sb.length() && start >= 0 && start < end) {
                sb.insert(end, "</b>");
                sb.insert(start, "<b>");
            }
        }
        return sb.toString();
    }

    @Override
    public void fetchMore(String word, Language sourceLanguage, Language targetLanguage, int page, OnResultListener listener) {
    }

    @Override
    public String getExtractionJs() {
        return new WiktionarySource().getExtractionJs();
    }

    private String wrapHtml(String content, String word) {
        String css = "body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; } " +
                     "h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; } " +
                     "h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; } " +
                     ".inflection { background: #f9f9f9; padding: 8px; border-radius: 4px; font-style: italic; margin-bottom: 1em; } " +
                     "ol { padding-left: 1.5em; } " +
                     "li.definition { margin-bottom: 0.5em; } " +
                     ".gloss { margin-bottom: 0.3em; } " +
                     "dl { margin-top: 0.5em; margin-bottom: 0.5em; } " +
                     ".h-usage-example { font-style: italic; display: block; margin-top: 0.5em; } " +
                     ".h-usage-example-translation { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; } " +
                     "a { color: #36c; text-decoration: none; } " +
                     ".example-checkbox { margin-right: 8px; vertical-align: middle; } " +
                     ".pronunciations ul { list-style: none; padding: 0; } " +
                     ".pronunciations li { margin-bottom: 4px; }";
        return "<html><head><style>" + css + "</style></head><body data-word='" + word + "'>" + content + "</body></html>";
    }

    private void copyDatabaseIfNeeded() {
        java.io.File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            try {
                dbFile.getParentFile().mkdirs();
                try (InputStream is = context.getAssets().open(DB_NAME);
                     OutputStream os = new FileOutputStream(dbFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy database", e);
            }
        }
    }
}
