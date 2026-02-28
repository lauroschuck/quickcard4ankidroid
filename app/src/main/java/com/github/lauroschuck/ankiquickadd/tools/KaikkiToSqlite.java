package com.github.lauroschuck.ankiquickadd.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Date;

/**
 * Script to convert Kaikki.org JSONL Wiktionary dump to relational SQLite Databases.
 * Usage: java KaikkiToSqlite <source_langs_csv> <targetLang1:dumpPath1> <targetLang2:dumpPath2> ...
 */
public class KaikkiToSqlite {

    private static final long MIN_HEADWORDS = 1000;

    private static class DatabaseSession implements AutoCloseable {
        final String dbPath;
        final Connection conn;
        final PreparedStatement pHeadword, pEntry, pGloss, pExample, pSenseLink, pOffset, pPronunciation, pRelation;
        final Map<String, Long> headwordCache = new HashMap<>();
        final Map<String, Integer> headwordSenseCounter = new HashMap<>();
        final Map<Long, Set<String>> headwordAudioCache = new HashMap<>();
        long headwordCount = 0;
        long glossCount = 0;
        long exampleCount = 0;

        DatabaseSession(String dbPath) throws Exception {
            this.dbPath = dbPath;
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

        void commit() throws Exception {
            pGloss.executeBatch(); pOffset.executeBatch(); pPronunciation.executeBatch(); pRelation.executeBatch(); pSenseLink.executeBatch();
            conn.commit();
        }

        @Override
        public void close() throws Exception {
            if (conn != null) conn.close();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java KaikkiToSqlite <source_langs_csv> <targetLang1:dumpPath1> <targetLang2:dumpPath2> ...");
            return;
        }

        Instant totalStart = Instant.now();

        String[] sourceLangs = args[0].split(",");
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String outDir = "out/" + timestamp;
        new File(outDir).mkdirs();

        KaikkiToSqlite converter = new KaikkiToSqlite();
        Map<String, Map<String, Boolean>> summaryTable = new TreeMap<>();

        String statsDbPath = outDir + File.separator + "stats.db";
        try (Connection statsConn = DriverManager.getConnection("jdbc:sqlite:" + statsDbPath)) {
            statsConn.setAutoCommit(false);
            setupStatsSchema(statsConn);
            statsConn.commit();

            PreparedStatement pStats = statsConn.prepareStatement("INSERT INTO stats (source_lang, target_lang, source_name, headwords, glosses, examples) VALUES (?, ?, ?, ?, ?, ?)");

            int totalDumps = args.length - 1;
            for (int i = 1; i < args.length; i++) {
                String[] pair = args[i].split(":", 2);
                if (pair.length < 2) continue;

                String targetLangCode = pair[0].toLowerCase().trim();
                String dumpPath = pair[1];

                Locale targetLocale = new Locale(targetLangCode);
                String targetLangName = targetLocale.getDisplayLanguage(Locale.ENGLISH);
                System.out.println(String.format(Locale.US, "\nStarting pass of dump: %s (Target: %s, %s)", dumpPath, targetLangCode, targetLangName));

                Instant dumpStart = Instant.now();
                Map<String, DatabaseSession> sessions = null;
                try {
                    sessions = converter.processDump(dumpPath, targetLangCode, sourceLangs, outDir, i, totalDumps);

                    for (Map.Entry<String, DatabaseSession> entry : sessions.entrySet()) {
                        String srcCode = entry.getKey();
                        DatabaseSession session = entry.getValue();
                        boolean kept = session.headwordCount >= MIN_HEADWORDS;
                        summaryTable.computeIfAbsent(srcCode, k -> new TreeMap<>()).put(targetLangCode, kept);

                        if (kept) {
                            String srcEngName = new Locale(srcCode).getDisplayLanguage(Locale.ENGLISH);
                            pStats.setString(1, srcCode);
                            pStats.setString(2, targetLangCode);
                            pStats.setString(3, srcEngName);
                            pStats.setLong(4, session.headwordCount);
                            pStats.setLong(5, session.glossCount);
                            pStats.setLong(6, session.exampleCount);
                            pStats.addBatch();
                        }
                    }
                    pStats.executeBatch();
                    statsConn.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Duration elapsed = Duration.between(dumpStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
                System.out.println(String.format(Locale.US, "Dump %s processed in %s", dumpPath, elapsed));
            }

            printSummaryTable(summaryTable);
            System.out.println("Stats saved to: " + statsDbPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Duration totalElapsed = Duration.between(totalStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
        System.out.println(String.format(Locale.US, "Entire process finished in %s", totalElapsed));
    }

    private static void setupStatsSchema(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("DROP TABLE IF EXISTS stats");
        st.execute("CREATE TABLE stats (source_lang TEXT, target_lang TEXT, source_name TEXT, headwords INTEGER, glosses INTEGER, examples INTEGER)");
    }

    private static void printSummaryTable(Map<String, Map<String, Boolean>> status) {
        if (status.isEmpty()) return;
        Set<String> targets = new TreeSet<>();
        for (Map<String, Boolean> map : status.values()) targets.addAll(map.keySet());

        System.out.println("\n--- EXTRACTION SUMMARY TABLE ---");
        System.out.print("src\\trg |");
        for (String t : targets) System.out.print(String.format(" %-4s |", t));
        System.out.println();

        for (Map.Entry<String, Map<String, Boolean>> entry : status.entrySet()) {
            System.out.print(String.format(" %-6s |", entry.getKey()));
            for (String t : targets) {
                Boolean kept = entry.getValue().get(t);
                System.out.print(kept != null && kept ? "  X   |" : "      |");
            }
            System.out.println();
        }
        System.out.println("--------------------------------\n");
    }

    public Map<String, DatabaseSession> processDump(String dumpPath, String targetLangCode, String[] sourceLangs, String outDir, int dumpIndex, int totalDumps) throws Exception {
        Map<String, DatabaseSession> sessions = new HashMap<>();
        for (String src : sourceLangs) {
            String srcLower = src.toLowerCase().trim();
            if (srcLower.equals(targetLangCode)) continue;
            sessions.put(srcLower, new DatabaseSession(outDir + File.separator + "wiktionary_kaikki_" + srcLower + "-" + targetLangCode + ".db"));
        }

        try (BufferedReader br = new BufferedReader(new FileReader(dumpPath))) {
            String line;
            long linesCount = 0;
            while ((line = br.readLine()) != null) {
                try {
                    JsonObject entry = JsonParser.parseString(line).getAsJsonObject();
                    String entryLangCode = entry.has("lang_code") ? entry.get("lang_code").getAsString().toLowerCase() : null;

                    if (entryLangCode != null && sessions.containsKey(entryLangCode)) {
                        processEntry(entry, sessions.get(entryLangCode));
                    }
                } catch (Exception ignored) {}

                if (++linesCount % 10000 == 0) {
                    for (DatabaseSession session : sessions.values()) session.commit();
                    System.out.print(String.format(Locale.US, "\r[%d/%d] %s : processed %d lines...", dumpIndex, totalDumps, targetLangCode, linesCount));
                }
            }
            System.out.print(String.format(Locale.US, "\r[%d/%d] %s : processed %d lines... Done.", dumpIndex, totalDumps, targetLangCode, linesCount));

            System.out.println("\nSummary for dump: " + dumpPath);
            for (Map.Entry<String, DatabaseSession> entry : sessions.entrySet()) {
                DatabaseSession session = entry.getValue();
                session.commit();
                String srcCode = entry.getKey();
                String srcEngName = new Locale(srcCode).getDisplayLanguage(Locale.ENGLISH);
                System.out.println(String.format(Locale.US, "- %s, %s: %d headwords, %d glosses, %d examples added.",
                        srcCode, srcEngName, session.headwordCount, session.glossCount, session.exampleCount));

                String dbPath = session.dbPath;
                session.close();
                if (session.headwordCount < MIN_HEADWORDS) {
                    System.out.println(String.format(Locale.US, "  ! Deleting database (below min %d): %s", MIN_HEADWORDS, dbPath));
                    new File(dbPath).delete();
                }
            }
        }
        return sessions;
    }

    private void processEntry(JsonObject entry, DatabaseSession session) throws Exception {
        if (!entry.has("word")) return;
        String word = entry.get("word").getAsString();
        long headwordId = getOrCreateHeadwordId(word, session);

        if (entry.has("sounds")) processSounds(headwordId, entry.getAsJsonArray("sounds"), session);
        if (entry.has("senses")) {
            String pos = entry.has("pos") ? entry.get("pos").getAsString() : "unknown";
            processSenses(headwordId, pos, entry.getAsJsonArray("senses"), word, session);
        }
    }

    private void processSounds(long headwordId, JsonArray sounds, DatabaseSession session) throws Exception {
        Set<String> seenUrls = session.headwordAudioCache.computeIfAbsent(headwordId, k -> new HashSet<>());
        for (JsonElement soundElem : sounds) {
            JsonObject sound = soundElem.getAsJsonObject();
            String audioUrl = sound.has("mp3_url") ? sound.get("mp3_url").getAsString() : (sound.has("ogg_url") ? sound.get("ogg_url").getAsString() : "");
            if (!audioUrl.isEmpty() && !seenUrls.contains(audioUrl)) {
                seenUrls.add(audioUrl);
                session.pPronunciation.setLong(1, headwordId);
                session.pPronunciation.setString(2, audioUrl);
                session.pPronunciation.setString(3, aggregateSoundDescription(sound));
                session.pPronunciation.addBatch();
            }
        }
    }

    private String aggregateSoundDescription(JsonObject sound) {
        StringBuilder sb = new StringBuilder();
        if (sound.has("text")) sb.append(sound.get("text").getAsString());
        if (sound.has("note")) { if (sb.length() > 0) sb.append("; "); sb.append(sound.get("note").getAsString()); }
        if (sound.has("tags")) {
            JsonArray tags = sound.getAsJsonArray("tags");
            if (tags.size() > 0) {
                if (sb.length() > 0) sb.append(" (");
                for (int i = 0; i < tags.size(); i++) { if (i > 0) sb.append(", "); sb.append(tags.get(i).getAsString()); }
                if (sb.indexOf(" (") != -1) sb.append(")");
            }
        }
        return sb.toString();
    }

    private void processSenses(long headwordId, String pos, JsonArray senses, String word, DatabaseSession session) throws Exception {
        int senseIdx = session.headwordSenseCounter.getOrDefault(word, 0);
        for (JsonElement senseElem : senses) {
            JsonObject sense = senseElem.getAsJsonObject();
            long entryId = insertLexicalEntry(headwordId, pos, senseIdx++, session);
            processGlosses(entryId, sense, session);
            processLinks(entryId, sense, session);
            processExamples(entryId, sense, session);
            processRelations(entryId, sense, session);
        }
        session.headwordSenseCounter.put(word, senseIdx);
    }

    private long insertLexicalEntry(long headwordId, String pos, int index, DatabaseSession session) throws Exception {
        session.pEntry.setLong(1, headwordId);
        session.pEntry.setString(2, pos);
        session.pEntry.setInt(3, index);
        session.pEntry.executeUpdate();
        try (ResultSet rs = session.pEntry.getGeneratedKeys()) { rs.next(); return rs.getLong(1); }
    }

    private void processGlosses(long entryId, JsonObject sense, DatabaseSession session) throws Exception {
        JsonArray glossArray = sense.has("raw_glosses") ? sense.getAsJsonArray("raw_glosses") : sense.getAsJsonArray("glosses");
        if (glossArray == null) return;
        for (int i = 0; i < glossArray.size(); i++) {
            session.pGloss.setLong(1, entryId);
            session.pGloss.setString(2, glossArray.get(i).getAsString());
            session.pGloss.setInt(3, i);
            session.pGloss.addBatch();
            session.glossCount++;
        }
    }

    private void processLinks(long entryId, JsonObject sense, DatabaseSession session) throws Exception {
        if (!sense.has("links")) return;
        for (JsonElement linkElem : sense.getAsJsonArray("links")) {
            JsonArray linkArr = linkElem.getAsJsonArray();
            if (linkArr.size() == 2) {
                String target = linkArr.get(1).getAsString();
                if (target.contains("#")) {
                    String lemma = target.substring(0, target.indexOf("#"));
                    session.pSenseLink.setLong(1, entryId);
                    session.pSenseLink.setString(2, linkArr.get(0).getAsString());
                    session.pSenseLink.setLong(3, getOrCreateHeadwordId(lemma, session));
                    session.pSenseLink.addBatch();
                }
            }
        }
    }

    private void processExamples(long entryId, JsonObject sense, DatabaseSession session) throws Exception {
        if (!sense.has("examples")) return;
        for (JsonElement exElem : sense.getAsJsonArray("examples")) {
            JsonObject ex = exElem.getAsJsonObject();
            if (ex.has("type") && "quotation".equalsIgnoreCase(ex.get("type").getAsString())) continue;
            String src = ex.has("text") ? ex.get("text").getAsString() : "";
            String trg = ex.has("english") ? ex.get("english").getAsString() : (ex.has("translation") ? ex.get("translation").getAsString() : "");
            if (!src.isEmpty() && !trg.isEmpty()) {
                session.pExample.setLong(1, entryId);
                session.pExample.setString(2, src);
                session.pExample.setString(3, trg);
                session.pExample.executeUpdate();
                long exId;
                try (ResultSet rs = session.pExample.getGeneratedKeys()) { rs.next(); exId = rs.getLong(1); }
                if (ex.has("bold_text_offsets")) saveOffsets(exId, "S", ex.getAsJsonArray("bold_text_offsets"), session);
                if (ex.has("bold_translation_offsets")) saveOffsets(exId, "T", ex.getAsJsonArray("bold_translation_offsets"), session);
                session.exampleCount++;
            }
        }
    }

    private void processRelations(long entryId, JsonObject sense, DatabaseSession session) throws Exception {
        saveRelationsBatch(entryId, "S", sense.getAsJsonArray("synonyms"), session);
        saveRelationsBatch(entryId, "A", sense.getAsJsonArray("antonyms"), session);
    }

    private void saveRelationsBatch(long entryId, String type, JsonArray array, DatabaseSession session) throws Exception {
        if (array == null) return;
        for (JsonElement e : array) {
            session.pRelation.setLong(1, entryId);
            session.pRelation.setString(2, type);
            session.pRelation.setString(3, e.getAsJsonObject().get("word").getAsString());
            session.pRelation.addBatch();
        }
    }

    private void saveOffsets(long exId, String type, JsonArray offsets, DatabaseSession session) throws Exception {
        for (JsonElement e : offsets) {
            JsonArray a = e.getAsJsonArray();
            session.pOffset.setLong(1, exId); session.pOffset.setString(2, type);
            session.pOffset.setInt(3, a.get(0).getAsInt()); session.pOffset.setInt(4, a.get(1).getAsInt());
            session.pOffset.addBatch();
        }
    }

    private long getOrCreateHeadwordId(String word, DatabaseSession session) throws Exception {
        if (session.headwordCache.containsKey(word)) return session.headwordCache.get(word);
        session.pHeadword.setString(1, word);
        int rows = session.pHeadword.executeUpdate();
        long id;
        try (ResultSet rs = session.pHeadword.getGeneratedKeys()) {
            if (rs.next()) {
                id = rs.getLong(1);
                session.headwordCount++;
            } else {
                try (PreparedStatement ps = session.conn.prepareStatement("SELECT id FROM headwords WHERE headword = ?")) {
                    ps.setString(1, word);
                    try (ResultSet r = ps.executeQuery()) { if (r.next()) id = r.getLong(1); else id = -1; }
                }
            }
        }
        session.headwordCache.put(word, id);
        return id;
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
