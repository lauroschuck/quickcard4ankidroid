package com.github.lauroschuck.ankiquickadd.processor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parallel version of KaikkiToSqlite with memory-safe backpressure.
 * Usage: java KaikkiProcessor <learning_langs_csv> <num_threads> <nativeLang1:last_mod:dumpPath1> ...
 */
public class KaikkiProcessor {

    private static final long MIN_HEADWORDS = 1000;
    private static final JsonObject POISON_PILL = new JsonObject();

    private static class DatabaseSession implements AutoCloseable {
        final String dbPath;
        final String learningLangCode;
        final String learningLangName;
        final Connection conn;
        final PreparedStatement pHeadword,
                pEntry,
                pGloss,
                pExample,
                pSenseLink,
                pOffset,
                pPronunciation,
                pRelation,
                pLanguage,
                pUpdateIpa;
        final Map<String, Long> headwordCache = new HashMap<>();
        final Map<String, Integer> headwordSenseCounter = new HashMap<>();
        final Map<Long, Set<String>> headwordAudioCache = new HashMap<>();
        final Map<Long, Set<String>> headwordIpaCache = new HashMap<>();
        final BlockingQueue<JsonObject> queue = new LinkedBlockingQueue<>(2000);
        final Thread writerThread;

        long headwordCount = 0;
        long glossCount = 0;
        long exampleCount = 0;
        long pronunciationCount = 0;
        long linkCount = 0;

        DatabaseSession(String dbPath, String learningLangCode) throws SQLException {
            this.dbPath = dbPath;
            this.learningLangCode = learningLangCode;
            this.learningLangName = Locale.forLanguageTag(learningLangCode).getDisplayLanguage(Locale.ENGLISH);
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // Adjustments for performance
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA synchronous = OFF");
                st.execute("PRAGMA journal_mode = MEMORY");
                st.execute("PRAGMA cache_size = 100000"); // Uses ~100MB of RAM for cache
                st.execute("PRAGMA temp_store = MEMORY");
            }
            this.conn.setAutoCommit(false);
            setupSchema(this.conn);

            this.pHeadword = conn.prepareStatement(
                    "INSERT OR IGNORE INTO headwords (headword) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            this.pEntry = conn.prepareStatement(
                    "INSERT INTO lexical_entries (headword_id, lexical_category, sense_index) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            this.pGloss = conn.prepareStatement(
                    "INSERT INTO glosses (lexical_entry_id, gloss, gloss_index) VALUES (?, ?, ?)");
            this.pExample = conn.prepareStatement(
                    "INSERT INTO examples (lexical_entry_id, learning_text, native_text) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            this.pSenseLink = conn.prepareStatement(
                    "INSERT INTO sense_links (lexical_entry_id, word, native_headword_id) VALUES (?, ?, ?)");
            this.pOffset = conn.prepareStatement(
                    "INSERT INTO bold_offsets (example_id, text_type, start_index, end_index) VALUES (?, ?, ?, ?)");
            this.pPronunciation = conn.prepareStatement(
                    "INSERT INTO pronunciations (headword_id, audio_url, description) VALUES (?, ?, ?)");
            this.pRelation =
                    conn.prepareStatement("INSERT INTO relations (lexical_entry_id, type, word) VALUES (?, ?, ?)");
            this.pLanguage = conn.prepareStatement("INSERT OR IGNORE INTO languages (iso, name) VALUES (?, ?)");
            this.pUpdateIpa = conn.prepareStatement("UPDATE headwords SET ipa = ? WHERE id = ?");

            this.writerThread = new Thread(this::consume);
            this.writerThread.start();
        }

        private void consume() {
            int batchSize = 0;
            try {
                while (true) {
                    JsonObject entry = queue.take();
                    if (entry == POISON_PILL) {
                        break;
                    }
                    processEntryInternal(entry);
                    if (++batchSize >= 1000) {
                        commitInternal();
                        batchSize = 0;
                    }
                }
                commitInternal();
            } catch (InterruptedException | SQLException e) {
                throw new RuntimeException("Consumption failure", e);
            }
        }

        private void processEntryInternal(JsonObject entry) throws SQLException {
            String word = entry.get("word").getAsString();
            long headwordId = getOrCreateHeadwordIdInternal(word);

            if (entry.has("lang") && entry.has("lang_code")) {
                pLanguage.setString(1, entry.get("lang_code").getAsString());
                pLanguage.setString(2, entry.get("lang").getAsString());
                pLanguage.addBatch();
            }

            if (entry.has("sounds")) {
                JsonArray sounds = entry.getAsJsonArray("sounds");
                processPronunciationsInternal(headwordId, sounds);
                processIpaInternal(headwordId, sounds);
            }
            if (entry.has("senses")) {
                String pos = entry.has("pos") ? entry.get("pos").getAsString() : "unknown";
                processSensesInternal(headwordId, pos, entry.getAsJsonArray("senses"), word);
            }
        }

        private void processPronunciationsInternal(long headwordId, JsonArray sounds) throws SQLException {
            Set<String> seenUrls = headwordAudioCache.computeIfAbsent(headwordId, k -> new HashSet<>());
            for (JsonElement soundElem : sounds) {
                JsonObject sound = soundElem.getAsJsonObject();
                String audioUrl = sound.has("mp3_url")
                        ? sound.get("mp3_url").getAsString()
                        : (sound.has("ogg_url") ? sound.get("ogg_url").getAsString() : "");
                if (!audioUrl.isEmpty() && !seenUrls.contains(audioUrl)) {
                    seenUrls.add(audioUrl);
                    pPronunciation.setLong(1, headwordId);
                    pPronunciation.setString(2, audioUrl);
                    pPronunciation.setString(3, aggregateSoundDescription(sound));
                    pPronunciation.addBatch();
                    pronunciationCount++;
                }
            }
        }

        private void processIpaInternal(long headwordId, JsonArray sounds) throws SQLException {
            List<String> ipas = new ArrayList<>();
            for (JsonElement soundElem : sounds) {
                JsonObject sound = soundElem.getAsJsonObject();
                if (sound.has("ipa")) {
                    String ipaText = sound.get("ipa").getAsString();
                    var tags = Stream.of("tags", "raw_tags")
                            .filter(sound::has)
                            .map(sound::getAsJsonArray)
                            .flatMap(ja -> ja.asList().stream())
                            .map(JsonElement::getAsString)
                            .distinct()
                            .collect(Collectors.toList());
                    String tagsText = (tags.isEmpty() ? "" : " (" + String.join(", ", tags) + ") ");
                    ipas.add(tagsText + ipaText);
                }
            }

            if (!ipas.isEmpty()) {
                // Same headword can be in multiple etymologies and lexical categories,
                // so since we merge that, we need to be able to merge all of them,
                // and the cache helps to find previous values and merge them.
                Set<String> cached = headwordIpaCache.getOrDefault(headwordId, new LinkedHashSet<>());
                boolean changed = cached.addAll(ipas);

                if (changed) {
                    pUpdateIpa.setString(1, String.join(", ", cached));
                    pUpdateIpa.setLong(2, headwordId);
                    pUpdateIpa.addBatch();
                }
            }
        }

        private void processSensesInternal(long headwordId, String pos, JsonArray senses, String word)
                throws SQLException {
            int senseIdx = headwordSenseCounter.getOrDefault(word, 0);
            for (JsonElement senseElem : senses) {
                JsonObject sense = senseElem.getAsJsonObject();
                long entryId = insertLexicalEntryInternal(headwordId, pos, senseIdx++);
                processGlossesInternal(entryId, sense);
                processLinksInternal(entryId, sense);
                processExamplesInternal(entryId, sense);
                processRelationsInternal(entryId, sense);
            }
            headwordSenseCounter.put(word, senseIdx);
        }

        private long insertLexicalEntryInternal(long headwordId, String pos, int index) throws SQLException {
            pEntry.setLong(1, headwordId);
            pEntry.setString(2, pos);
            pEntry.setInt(3, index);
            pEntry.executeUpdate();
            try (ResultSet rs = pEntry.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }

        private void processGlossesInternal(long entryId, JsonObject sense) throws SQLException {
            JsonArray glossArray =
                    sense.has("raw_glosses") ? sense.getAsJsonArray("raw_glosses") : sense.getAsJsonArray("glosses");
            if (glossArray == null) {
                return;
            }
            for (int i = 0; i < glossArray.size(); i++) {
                pGloss.setLong(1, entryId);
                pGloss.setString(2, glossArray.get(i).getAsString());
                pGloss.setInt(3, i);
                pGloss.addBatch();
                glossCount++;
            }
        }

        private void processLinksInternal(long entryId, JsonObject sense) throws SQLException {
            if (!sense.has("links")) {
                return;
            }
            for (JsonElement linkElem : sense.getAsJsonArray("links")) {
                JsonArray linkArr = linkElem.getAsJsonArray();
                if (linkArr.size() == 2) {
                    String target = linkArr.get(1).getAsString();
                    if (target.contains("#")) {
                        String lemma = target.substring(0, target.indexOf("#"));
                        pSenseLink.setLong(1, entryId);
                        pSenseLink.setString(2, linkArr.get(0).getAsString());
                        pSenseLink.setLong(3, getOrCreateHeadwordIdInternal(lemma));
                        pSenseLink.addBatch();
                        linkCount++;
                    }
                }
            }
        }

        private void processExamplesInternal(long entryId, JsonObject sense) throws SQLException {
            if (!sense.has("examples")) {
                return;
            }
            for (JsonElement exElem : sense.getAsJsonArray("examples")) {
                JsonObject ex = exElem.getAsJsonObject();
                if (ex.has("type")
                        && "quotation".equalsIgnoreCase(ex.get("type").getAsString())) {
                    continue;
                }
                String src = ex.has("text") ? ex.get("text").getAsString() : "";
                String trg = ex.has("english")
                        ? ex.get("english").getAsString()
                        : (ex.has("translation") ? ex.get("translation").getAsString() : "");
                if (!src.isEmpty() && !trg.isEmpty()) {
                    pExample.setLong(1, entryId);
                    pExample.setString(2, src);
                    pExample.setString(3, trg);
                    pExample.executeUpdate();
                    long exId;
                    try (ResultSet rs = pExample.getGeneratedKeys()) {
                        rs.next();
                        exId = rs.getLong(1);
                    }
                    if (ex.has("bold_text_offsets"))
                        saveOffsetsInternal(exId, "L", ex.getAsJsonArray("bold_text_offsets"));
                    if (ex.has("bold_translation_offsets"))
                        saveOffsetsInternal(exId, "N", ex.getAsJsonArray("bold_translation_offsets"));
                    exampleCount++;
                }
            }
        }

        private void processRelationsInternal(long entryId, JsonObject sense) throws SQLException {
            saveRelationsBatchInternal(entryId, "S", sense.getAsJsonArray("synonyms"));
            saveRelationsBatchInternal(entryId, "A", sense.getAsJsonArray("antonyms"));
        }

        private void saveRelationsBatchInternal(long entryId, String type, JsonArray array) throws SQLException {
            if (array == null) {
                return;
            }
            for (JsonElement e : array) {
                pRelation.setLong(1, entryId);
                pRelation.setString(2, type);
                pRelation.setString(3, e.getAsJsonObject().get("word").getAsString());
                pRelation.addBatch();
            }
        }

        private void saveOffsetsInternal(long exId, String type, JsonArray offsets) throws SQLException {
            for (JsonElement e : offsets) {
                JsonArray a = e.getAsJsonArray();
                pOffset.setLong(1, exId);
                pOffset.setString(2, type);
                pOffset.setInt(3, a.get(0).getAsInt());
                pOffset.setInt(4, a.get(1).getAsInt());
                pOffset.addBatch();
            }
        }

        private long getOrCreateHeadwordIdInternal(String word) throws SQLException {
            var cachedId = headwordCache.get(word);
            if (cachedId != null) {
                return cachedId;
            }

            pHeadword.setString(1, word);
            int rows = pHeadword.executeUpdate();
            long id;
            try (ResultSet rs = pHeadword.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getLong(1);
                    headwordCount++;
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM headwords WHERE headword = ?")) {
                        ps.setString(1, word);
                        try (ResultSet r = ps.executeQuery()) {
                            if (r.next()) {
                                id = r.getLong(1);
                            } else {
                                id = -1;
                            }
                        }
                    }
                }
            }
            headwordCache.put(word, id);
            return id;
        }

        private void commitInternal() throws SQLException {
            pGloss.executeBatch();
            pOffset.executeBatch();
            pPronunciation.executeBatch();
            pRelation.executeBatch();
            pSenseLink.executeBatch();
            pLanguage.executeBatch();
            pUpdateIpa.executeBatch();
            conn.commit();
        }

        @Override
        public void close() throws SQLException, InterruptedException {
            queue.put(POISON_PILL);
            writerThread.join();
            if (conn != null) {
                conn.close();
            }
        }
    }

    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        if (args.length < 5) {
            System.out.println(
                    "Usage: java KaikkiProcessor <version> <learning_langs_csv> <num_threads> <mirrors_csv> <nativeLang1:last_mod:dumpPath1> ...");
            return;
        }

        Instant totalStart = Instant.now();
        long epochSeconds = totalStart.getEpochSecond();
        String version = args[0];
        String[] learningLangs = args[1].split(",");
        int numThreads = Integer.parseInt(args[2]);
        String[] mirrorBases = args[3].isEmpty() ? new String[0] : args[3].split(",");
        String timestampDir = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

        // Find the absolute root of the project to ensure output goes to project-root/processor/out/
        String currentDir = System.getProperty("user.dir");
        String outBase = currentDir.endsWith("processor") ? "out" : "processor/out";
        String outDir = outBase + File.separator + timestampDir;
        new File(outDir).mkdirs();

        KaikkiProcessor converter = new KaikkiProcessor();
        Map<String, Map<String, Boolean>> summaryTable = new TreeMap<>();
        List<Map<String, Object>> dictionaryMetadataList = new ArrayList<>();

        int totalDumps = args.length - 4;
        for (int i = 4; i < args.length; i++) {
            String[] parts = args[i].split(":", 3);
            if (parts.length < 3) {
                System.err.println("Invalid argument format: " + args[i]);
                continue;
            }

            String nativeLangCode = parts[0].toLowerCase().trim();
            String lastModRaw = parts[1];
            String dumpPath = parts[2];

            var lastModEpoch = parseTimestampToEpoch(lastModRaw);
            var lastModInstant = Instant.ofEpochSecond(lastModEpoch);

            Locale nativeLocale = new Locale(nativeLangCode);
            String nativeLangName = nativeLocale.getDisplayLanguage(Locale.ENGLISH);
            System.out.println(String.format(
                    Locale.US,
                    "\nStarting pass of dump: %s (Native: %s, %s, Mod: %s) with %d threads",
                    dumpPath,
                    nativeLangCode,
                    nativeLangName,
                    lastModInstant,
                    numThreads));

            Instant dumpStart = Instant.now();
            Map<String, DatabaseSession> sessions = converter.processDumpParallel(
                    dumpPath,
                    nativeLangCode,
                    learningLangs,
                    outDir,
                    i - 3,
                    totalDumps,
                    numThreads,
                    version,
                    lastModEpoch);

            for (Map.Entry<String, DatabaseSession> entry : sessions.entrySet()) {
                String learningCode = entry.getKey();
                DatabaseSession session = entry.getValue();
                boolean kept = session.headwordCount >= MIN_HEADWORDS;
                summaryTable
                        .computeIfAbsent(learningCode, k -> new TreeMap<>())
                        .put(nativeLangCode, kept);

                if (kept) {
                    File dbFile = new File(session.dbPath);
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("learning_lang", learningCode);
                    meta.put("native_lang", nativeLangCode);
                    meta.put("file", dbFile.getName());
                    meta.put("last_modified", lastModEpoch);
                    meta.put("last_modified_iso", lastModInstant.toString());
                    meta.put("size_bytes", dbFile.length());
                    meta.put("headwords", session.headwordCount);
                    meta.put("glosses", session.glossCount);
                    meta.put("examples", session.exampleCount);
                    meta.put("pronunciations", session.pronunciationCount);
                    dictionaryMetadataList.add(meta);
                }
            }

            Duration elapsed = Duration.between(dumpStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
            System.out.println(String.format(Locale.US, "Dump %s processed in %s", dumpPath, elapsed));
        }

        writeMetadataJson(outDir, dictionaryMetadataList, epochSeconds, version, mirrorBases);
        printSummaryTable(summaryTable);

        Duration totalElapsed = Duration.between(totalStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
        System.out.println(String.format(Locale.US, "Entire process finished in %s", totalElapsed));
    }

    private static long parseTimestampToEpoch(String raw) {
        // Input format: yyyyMMdd-HHmmss
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
        return Instant.from(inputFormatter.parse(raw)).getEpochSecond();
    }

    private static void writeMetadataJson(
            String outDir,
            List<Map<String, Object>> dictionaries,
            long epochSeconds,
            String version,
            String[] mirrorBases) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", epochSeconds);
        root.put("timestamp_iso", Instant.ofEpochSecond(epochSeconds).toString());

        List<String> fullMirrors = new ArrayList<>();
        for (String base : mirrorBases) {
            // Format: <base_url>/<version>/<timestamp>
            String fullUrl = base;
            if (!fullUrl.endsWith("/")) {
                fullUrl += "/";
            }
            fullUrl += version + "/" + epochSeconds;
            fullMirrors.add(fullUrl);
        }
        root.put("mirrors", fullMirrors);
        root.put("dictionaries", dictionaries);

        File metaFile = new File(outDir, "metadata.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(metaFile)) {
            gson.toJson(root, writer);
            System.out.println("\nMetadata saved to: " + metaFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write metadata.json", e);
        }
    }

    private Map<String, DatabaseSession> processDumpParallel(
            String dumpPath,
            String nativeLangCode,
            String[] learningLangs,
            String outDir,
            int dumpIndex,
            int totalDumps,
            int numThreads,
            String version,
            long lastModEpoch) throws SQLException, IOException, InterruptedException {
        Map<String, DatabaseSession> sessions = new HashMap<>();
        for (String learning : learningLangs) {
            String learningLower = learning.toLowerCase().trim();
            if (learningLower.equals(nativeLangCode)) continue;
            String dbFileName = String.format(
                    "wiktionary_kaikki_%s-%s_%s_%d.db", learningLower, nativeLangCode, version, lastModEpoch);
            sessions.put(
                    learningLower,
                    new DatabaseSession(outDir + File.separator + dbFileName, learningLower));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Semaphore semaphore = new Semaphore(numThreads * 2);
        AtomicLong linesCount = new AtomicLong(0);
        AtomicLong irrelevantLinesCount = new AtomicLong(0);

        try (BufferedReader br = new BufferedReader(new FileReader(dumpPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String currentLine = line;
                semaphore.acquire();
                executor.submit(() -> {
                    try {
                        JsonObject entry = JsonParser.parseString(currentLine).getAsJsonObject();
                        String entryLangCode = entry.has("lang_code")
                                ? entry.get("lang_code").getAsString().toLowerCase()
                                : null;
                        if (entryLangCode != null && sessions.containsKey(entryLangCode)) {
                            sessions.get(entryLangCode).queue.put(entry);
                        } else {
                            irrelevantLinesCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing line: " + e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                    long current = linesCount.incrementAndGet();
                    if (current % 10000 == 0) {
                        System.out.print(String.format(
                                Locale.US,
                                "\r[%d/%d] %s : processed %d lines...",
                                dumpIndex,
                                totalDumps,
                                nativeLangCode,
                                current));
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            System.out.print(String.format(
                    Locale.US,
                    "\r[%d/%d] %s : processed %d lines (%d irrelevant, %.2f%%)... Done.",
                    dumpIndex,
                    totalDumps,
                    nativeLangCode,
                    linesCount.get(),
                    irrelevantLinesCount.get(),
                    100.0 * irrelevantLinesCount.get() / linesCount.get()));

            System.out.println("\nSummary for dump: " + dumpPath);
            for (Map.Entry<String, DatabaseSession> entry : sessions.entrySet()) {
                DatabaseSession session = entry.getValue();
                session.close();
                String learningCode = entry.getKey();
                System.out.println(String.format(
                        Locale.US,
                        "- %s, %s: %d headwords, %d glosses, %d examples, %d pronunciations, %d links added.",
                        learningCode,
                        session.learningLangName,
                        session.headwordCount,
                        session.glossCount,
                        session.exampleCount,
                        session.pronunciationCount,
                        session.linkCount));
                if (session.headwordCount < MIN_HEADWORDS) {
                    System.out.println(String.format(
                            Locale.US, "  ! Deleting database (below min %d): %s", MIN_HEADWORDS, session.dbPath));
                    new File(session.dbPath).delete();
                }
            }
        }
        return sessions;
    }

    private static String aggregateSoundDescription(JsonObject sound) {
        StringBuilder sb = new StringBuilder();
        if (sound.has("text")) sb.append(sound.get("text").getAsString());
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
        return sb.toString();
    }

    private static void setupSchema(Connection conn) throws SQLException {
        Statement st = conn.createStatement();
        st.execute("DROP TABLE IF EXISTS relations");
        st.execute("DROP TABLE IF EXISTS bold_offsets");
        st.execute("DROP TABLE IF EXISTS sense_links");
        st.execute("DROP TABLE IF EXISTS examples");
        st.execute("DROP TABLE IF EXISTS glosses");
        st.execute("DROP TABLE IF EXISTS lexical_entries");
        st.execute("DROP TABLE IF EXISTS pronunciations");
        st.execute("DROP TABLE IF EXISTS headwords");
        st.execute("DROP TABLE IF EXISTS languages");
        st.execute("CREATE TABLE headwords (id INTEGER PRIMARY KEY AUTOINCREMENT, headword TEXT UNIQUE, ipa TEXT)");
        st.execute(
                "CREATE TABLE lexical_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, headword_id INTEGER, lexical_category TEXT, sense_index INTEGER, FOREIGN KEY(headword_id) REFERENCES headwords(id))");
        st.execute(
                "CREATE TABLE glosses (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, gloss TEXT, gloss_index INTEGER, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");
        st.execute(
                "CREATE TABLE examples (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, learning_text TEXT, native_text TEXT, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");
        st.execute(
                "CREATE TABLE sense_links (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, word TEXT, native_headword_id INTEGER, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id), FOREIGN KEY(native_headword_id) REFERENCES headwords(id))");
        st.execute(
                "CREATE TABLE bold_offsets (id INTEGER PRIMARY KEY AUTOINCREMENT, example_id INTEGER, text_type CHAR(1), start_index INTEGER, end_index INTEGER, FOREIGN KEY(example_id) REFERENCES examples(id))");
        st.execute(
                "CREATE TABLE pronunciations (id INTEGER PRIMARY KEY AUTOINCREMENT, headword_id INTEGER, audio_url TEXT, description TEXT, FOREIGN KEY(headword_id) REFERENCES headwords(id))");
        st.execute(
                "CREATE TABLE relations (id INTEGER PRIMARY KEY AUTOINCREMENT, lexical_entry_id INTEGER, type CHAR(1), word TEXT, FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id))");
        st.execute("CREATE TABLE languages (iso TEXT PRIMARY KEY, name TEXT)");
        st.execute("CREATE INDEX idx_hw_word ON headwords(headword)");
        st.execute("CREATE INDEX idx_le_hw ON lexical_entries(headword_id)");
        st.execute("CREATE INDEX idx_gl_le ON glosses(lexical_entry_id)");
        st.execute("CREATE INDEX idx_ex_le ON examples(lexical_entry_id)");
        st.execute("CREATE INDEX idx_sl_le ON sense_links(lexical_entry_id)");
    }

    private static void setupStatsSchema(Connection conn) throws SQLException {
        Statement st = conn.createStatement();
        st.execute("DROP TABLE IF EXISTS stats");
        st.execute(
                "CREATE TABLE stats (learning_lang TEXT, native_lang TEXT, learning_lang_name TEXT, headwords INTEGER, glosses INTEGER, examples INTEGER)");
    }

    private static void printSummaryTable(Map<String, Map<String, Boolean>> status) {
        if (status.isEmpty()) return;
        Set<String> natives = new TreeSet<>();
        for (Map<String, Boolean> map : status.values()) natives.addAll(map.keySet());
        System.out.println("\n--- EXTRACTION SUMMARY TABLE ---");
        System.out.print("lrn\\nat |");
        for (String t : natives) System.out.print(String.format(" %-4s |", t));
        System.out.println();
        for (Map.Entry<String, Map<String, Boolean>> entry : status.entrySet()) {
            System.out.print(String.format(" %-6s |", entry.getKey()));
            for (String t : natives) {
                Boolean kept = entry.getValue().get(t);
                System.out.print(kept != null && kept ? "  X   |" : "      |");
            }
            System.out.println();
        }
        System.out.println("--------------------------------\n");
    }
}
