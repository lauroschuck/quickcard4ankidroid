package com.github.lauroschuck.ankiquickadd.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Script to convert Kaikki.org JSONL Wiktionary dump to a relational SQLite Database.
 * Processes 20GB+ files using streaming and batch inserts.
 */
public class KaikkiToSqlite {

    private final Connection conn;
    private final PreparedStatement pHeadword;
    private final PreparedStatement pEntry;
    private final PreparedStatement pGloss;
    private final PreparedStatement pExample;
    private final PreparedStatement pSenseLink;
    private final PreparedStatement pOffset;
    private final PreparedStatement pPronunciation;
    private final PreparedStatement pRelation;

    private final Map<String, Long> headwordCache = new HashMap<>();
    private final Map<String, Integer> headwordSenseCounter = new HashMap<>();
    // Cache to prevent duplicate audio URLs for the same headword across multiple etymologies
    private final Map<Long, Set<String>> headwordAudioCache = new HashMap<>();

    public KaikkiToSqlite(String dbPath) throws Exception {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.conn.setAutoCommit(false);
        setupSchema(conn);

        this.pHeadword = conn.prepareStatement("INSERT OR IGNORE INTO headwords (headword) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
        this.pEntry = conn.prepareStatement("INSERT INTO lexical_entries (headword_id, lexical_category, sense_index) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
        this.pGloss = conn.prepareStatement("INSERT INTO glosses (lexical_entry_id, gloss, gloss_index) VALUES (?, ?, ?)");
        this.pExample = conn.prepareStatement("INSERT INTO examples (lexical_entry_id, source_text, target_text) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
        this.pSenseLink = conn.prepareStatement("INSERT INTO sense_links (lexical_entry_id, word, target_headword_id) VALUES (?, ?, ?)");
        this.pOffset = conn.prepareStatement("INSERT INTO bold_offsets (example_id, is_translation, start_index, end_index) VALUES (?, ?, ?, ?)");
        this.pPronunciation = conn.prepareStatement("INSERT INTO pronunciations (headword_id, audio_url, description) VALUES (?, ?, ?)");
        this.pRelation = conn.prepareStatement("INSERT INTO relations (lexical_entry_id, type, word) VALUES (?, ?, ?)");
    }

    public static void main(String[] args) {
        String inputJsonl = "/home/lauro/AndroidStudioProjects/SwedishAnkiQuickAdd/last-50000.json";
        String outputDb = "swedish_dict.db";

        try {
            KaikkiToSqlite converter = new KaikkiToSqlite(outputDb);
            converter.run(inputJsonl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(String inputJsonl) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(inputJsonl))) {
            String line;
            long linesCount = 0;
            while ((line = br.readLine()) != null) {
                try {
                    JsonObject entry = JsonParser.parseString(line).getAsJsonObject();
                    if (isSwedish(entry)) {
                        processEntry(entry);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing line " + (linesCount + 1) + ": " + e.getMessage());
                }

                if (++linesCount % 10000 == 0) {
                    commitBatch();
                    System.out.println("Processed " + linesCount + " lines...");
                }
            }
            commitBatch();
            System.out.println("Finished! Total lines processed: " + linesCount);
        }
    }

    private boolean isSwedish(JsonObject entry) {
        String langCode = entry.has("lang_code") ? entry.get("lang_code").getAsString() : null;
        String langName = entry.has("lang") ? entry.get("lang").getAsString() : null;
        return ("sv".equalsIgnoreCase(langCode) || "Swedish".equalsIgnoreCase(langName)) && entry.has("word");
    }

    private void processEntry(JsonObject entry) throws Exception {
        String word = entry.get("word").getAsString();
        long headwordId = getOrCreateHeadwordId(word);

        if (entry.has("sounds")) {
            processPronunciations(headwordId, entry.getAsJsonArray("sounds"));
        }

        if (entry.has("senses")) {
            String pos = entry.has("pos") ? entry.get("pos").getAsString() : "unknown";
            processSenses(headwordId, pos, entry.getAsJsonArray("senses"), word);
        }
    }

    private void processPronunciations(long headwordId, JsonArray sounds) throws Exception {
        Set<String> seenUrls = headwordAudioCache.computeIfAbsent(headwordId, k -> new HashSet<>());

        for (JsonElement soundElem : sounds) {
            JsonObject sound = soundElem.getAsJsonObject();
            // MP3 is generally preferable for broader compatibility
            String audioUrl = sound.has("mp3_url") ? sound.get("mp3_url").getAsString() :
                             (sound.has("ogg_url") ? sound.get("ogg_url").getAsString() : "");
            
            if (!audioUrl.isEmpty() && !seenUrls.contains(audioUrl)) {
                seenUrls.add(audioUrl);
                pPronunciation.setLong(1, headwordId);
                pPronunciation.setString(2, audioUrl);
                
                // Aggregate description/explanation from all sources
                StringBuilder sb = new StringBuilder();
                if (sound.has("text")) {
                    sb.append(sound.get("text").getAsString());
                }
                if (sound.has("note")) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(sound.get("note").getAsString());
                }
                if (sound.has("tags")) {
                    JsonArray tags = sound.getAsJsonArray("tags");
                    if (tags.size() > 0) {
                        if (sb.length() > 0) sb.append(" (");
                        for (int i = 0; i < tags.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(tags.get(i).getAsString());
                        }
                        if (sb.indexOf(" (") != -1) sb.append(")");
                    }
                }

                pPronunciation.setString(3, sb.toString());
                pPronunciation.addBatch();
            }
        }
    }

    private void processSenses(long headwordId, String pos, JsonArray senses, String word) throws Exception {
        int senseIdx = headwordSenseCounter.getOrDefault(word, 0);

        for (JsonElement senseElem : senses) {
            JsonObject sense = senseElem.getAsJsonObject();
            long entryId = createLexicalEntry(headwordId, pos, senseIdx++);
            
            processGlosses(entryId, sense);
            processSenseLinks(entryId, sense);
            processExamples(entryId, sense);
            processRelations(entryId, sense);
        }
        
        headwordSenseCounter.put(word, senseIdx);
    }

    private long createLexicalEntry(long headwordId, String pos, int senseIndex) throws Exception {
        pEntry.setLong(1, headwordId);
        pEntry.setString(2, pos);
        pEntry.setInt(3, senseIndex);
        pEntry.executeUpdate();
        try (ResultSet rs = pEntry.getGeneratedKeys()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void processGlosses(long entryId, JsonObject sense) throws Exception {
        JsonArray glossArray = sense.has("raw_glosses") ? sense.getAsJsonArray("raw_glosses") : sense.getAsJsonArray("glosses");
        if (glossArray == null) return;

        for (int i = 0; i < glossArray.size(); i++) {
            pGloss.setLong(1, entryId);
            pGloss.setString(2, glossArray.get(i).getAsString());
            pGloss.setInt(3, i);
            pGloss.addBatch();
        }
    }

    private void processSenseLinks(long entryId, JsonObject sense) throws Exception {
        if (!sense.has("links")) return;

        for (JsonElement linkElem : sense.getAsJsonArray("links")) {
            JsonArray linkArr = linkElem.getAsJsonArray();
            if (linkArr.size() == 2) {
                String linkWord = linkArr.get(0).getAsString();
                String linkTarget = linkArr.get(1).getAsString();
                if (linkTarget.contains("#Swedish")) {
                    String cleanLemma = linkTarget.substring(0, linkTarget.indexOf("#Swedish"));
                    long targetId = getOrCreateHeadwordId(cleanLemma);
                    pSenseLink.setLong(1, entryId);
                    pSenseLink.setString(2, linkWord);
                    pSenseLink.setLong(3, targetId);
                    pSenseLink.addBatch();
                }
            }
        }
    }

    private void processExamples(long entryId, JsonObject sense) throws Exception {
        if (!sense.has("examples")) return;

        for (JsonElement exElem : sense.getAsJsonArray("examples")) {
            JsonObject ex = exElem.getAsJsonObject();
            if (ex.has("type") && "quotation".equalsIgnoreCase(ex.get("type").getAsString())) continue;

            String src = ex.has("text") ? ex.get("text").getAsString() : "";
            String trg = ex.has("english") ? ex.get("english").getAsString() : 
                         (ex.has("translation") ? ex.get("translation").getAsString() : "");

            if (!src.isEmpty()) {
                long exampleId = insertExample(entryId, src, trg);
                processExampleOffsets(exampleId, ex);
            }
        }
    }

    private long insertExample(long entryId, String src, String trg) throws Exception {
        pExample.setLong(1, entryId);
        pExample.setString(2, src);
        pExample.setString(3, trg);
        pExample.executeUpdate();
        try (ResultSet rs = pExample.getGeneratedKeys()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void processExampleOffsets(long exampleId, JsonObject ex) throws Exception {
        if (ex.has("bold_text_offsets")) {
            saveOffsetsBatch(exampleId, "S", ex.getAsJsonArray("bold_text_offsets"));
        }
        if (ex.has("bold_translation_offsets")) {
            saveOffsetsBatch(exampleId, "T", ex.getAsJsonArray("bold_translation_offsets"));
        }
    }

    private void processRelations(long entryId, JsonObject sense) throws Exception {
        saveRelations(entryId, "S", sense.getAsJsonArray("synonyms"));
        saveRelations(entryId, "A", sense.getAsJsonArray("antonyms"));
    }

    private void saveRelations(long entryId, String type, JsonArray array) throws Exception {
        if (array == null) return;
        for (JsonElement e : array) {
            pRelation.setLong(1, entryId);
            pRelation.setString(2, type);
            pRelation.setString(3, e.getAsJsonObject().get("word").getAsString());
            pRelation.addBatch();
        }
    }

    private void saveOffsetsBatch(long exampleId, String type, JsonArray offsets) throws Exception {
        for (JsonElement e : offsets) {
            JsonArray a = e.getAsJsonArray();
            pOffset.setLong(1, exampleId);
            pOffset.setString(2, type);
            pOffset.setInt(3, a.get(0).getAsInt());
            pOffset.setInt(4, a.get(1).getAsInt());
            pOffset.addBatch();
        }
    }

    private long getOrCreateHeadwordId(String word) throws Exception {
        if (headwordCache.containsKey(word)) return headwordCache.get(word);
        
        pHeadword.setString(1, word);
        pHeadword.executeUpdate();
        long id;
        try (ResultSet rs = pHeadword.getGeneratedKeys()) {
            if (rs.next()) {
                id = rs.getLong(1);
            } else {
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM headwords WHERE headword = ?")) {
                    ps.setString(1, word);
                    try (ResultSet r = ps.executeQuery()) {
                        if (r.next()) id = r.getLong(1);
                        else id = -1;
                    }
                }
            }
        }
        headwordCache.put(word, id);
        return id;
    }

    private void commitBatch() throws Exception {
        pGloss.executeBatch();
        pOffset.executeBatch();
        pPronunciation.executeBatch();
        pRelation.executeBatch();
        pSenseLink.executeBatch();
        conn.commit();
    }

    private static void setupSchema(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("DROP TABLE IF EXISTS relations");
        st.execute("DROP TABLE IF EXISTS bold_offsets");
        st.execute("DROP TABLE IF EXISTS sense_links");
        st.execute("DROP TABLE IF EXISTS examples");
        st.execute("DROP TABLE IF EXISTS glosses");
        st.execute("DROP TABLE IF EXISTS lexical_entries");
        st.execute("DROP TABLE IF EXISTS pronunciations");
        st.execute("DROP TABLE IF EXISTS headwords");

        st.execute("CREATE TABLE headwords (id INTEGER PRIMARY KEY AUTOINCREMENT, headword TEXT UNIQUE)");
        st.execute("CREATE TABLE lexical_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, headword_id INTEGER, lexical_category TEXT, sense_index INTEGER, FOREIGN KEY(headword_id) REFERENCES headwords(id))");
        st.execute("CREATE TABLE glosses (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, gloss TEXT, gloss_index INTEGER, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");
        st.execute("CREATE TABLE examples (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, source_text TEXT, target_text TEXT, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");
        st.execute("CREATE TABLE sense_links (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, word TEXT, target_headword_id INTEGER, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id), FOREIGN KEY(target_headword_id) REFERENCES headwords(id))");
        st.execute("CREATE TABLE bold_offsets (id INTEGER PRIMARY KEY AUTOINCREMENT, example_id INTEGER, is_translation CHAR(1), start_index INTEGER, end_index INTEGER, FOREIGN KEY(example_id) REFERENCES examples(id))");
        st.execute("CREATE TABLE pronunciations (id INTEGER PRIMARY KEY AUTOINCREMENT, headword_id INTEGER, audio_url TEXT, description TEXT, FOREIGN KEY(headword_id) REFERENCES headwords(id))");
        st.execute("CREATE TABLE relations (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, type CHAR(1), word TEXT, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");

        st.execute("CREATE INDEX idx_hw_word ON headwords(headword)");
        st.execute("CREATE INDEX idx_le_hw ON lexical_entries(headword_id)");
        st.execute("CREATE INDEX idx_gl_le ON glosses(lexical_entry_id)");
        st.execute("CREATE INDEX idx_ex_le ON examples(lexical_entry_id)");
        st.execute("CREATE INDEX idx_sl_le ON sense_links(lexical_entry_id)");
    }
}
