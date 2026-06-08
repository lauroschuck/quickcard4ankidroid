package com.github.lauroschuck.quickcard4ankidroid.processor;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import java.util.concurrent.ConcurrentHashMap;
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

    private static final long MIN_HEADWORDS = 10000;
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

        long headwordCount = -1;
        long glossCount = 0;
        long exampleCount = 0;
        long pronunciationCount = 0;
        long linkCount = 0;
        long formOfCount = 0;
        long deletedHeadwords = -1;
        long deletedLexicalEntries = -1;
        long deletedLinks = -1;
        long collectiveLinkCount = -1;
        boolean removed = false;

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
                    "INSERT INTO lexical_entries (headword_id, pos, pos_title, sense_index) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            this.pGloss = conn.prepareStatement(
                    "INSERT INTO glosses (lexical_entry_id, gloss, gloss_index) VALUES (?, ?, ?)");
            this.pExample = conn.prepareStatement(
                    "INSERT INTO examples (lexical_entry_id, learning_text, native_text) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            this.pSenseLink = conn.prepareStatement(
                    "INSERT OR IGNORE INTO sense_links (lexical_entry_id, word, native_headword_id) VALUES (?, ?, ?)");
            this.pOffset = conn.prepareStatement(
                    "INSERT OR IGNORE INTO bold_offsets (example_id, text_type, start_index, end_index) VALUES (?, ?, ?, ?)");
            this.pPronunciation = conn.prepareStatement(
                    "INSERT OR IGNORE INTO pronunciations (headword_id, audio_url, description) VALUES (?, ?, ?)");
            this.pRelation =
                    conn.prepareStatement("INSERT OR IGNORE INTO relations (lexical_entry_id, type, word) VALUES (?, ?, ?)");
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
                cleanDeadEnds();
            } catch (InterruptedException | SQLException e) {
                throw new RuntimeException("Consumption failure", e);
            }
        }

        private void processEntryInternal(JsonObject entry) throws SQLException {
            String word = entry.get("word").getAsString();
            long headwordId = getOrCreateHeadwordIdInternal(word);

            pLanguage.setString(1, entry.get("lang_code").getAsString());
            pLanguage.setString(2, entry.get("lang").getAsString());
            pLanguage.addBatch();

            if (entry.has("sounds")) {
                JsonArray sounds = entry.getAsJsonArray("sounds");
                processPronunciationsInternal(headwordId, sounds);
                processIpaInternal(headwordId, sounds);
            }
            if (entry.has("senses")) {
                String pos = entry.get("pos").getAsString().trim();
                if (pos.isEmpty()) {
                    throw new IllegalArgumentException("POS is mandatory");
                }
                String posTitle = entry.has("pos_title") ? entry.get("pos_title").getAsString().trim() : "";
                processSensesInternal(headwordId, pos, posTitle, entry.getAsJsonArray("senses"), word);
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

        private static String aggregateSoundDescription(JsonObject sound) {
            var tags = extractTagString(sound);
            var prefix = tags.isEmpty() ? "" : tags + " ";

            return Stream.of("text", "note")
                    .filter(sound::has)
                    .map(sound::get)
                    .map(JsonElement::getAsString)
                    .distinct()
                    .collect(Collectors.joining("; ", prefix, ""))
                    .trim();
        }

        private void processIpaInternal(long headwordId, JsonArray sounds) throws SQLException {
            List<String> ipas = new ArrayList<>();
            for (JsonElement soundElem : sounds) {
                JsonObject sound = soundElem.getAsJsonObject();
                if (sound.has("ipa")) {
                    String ipaText = sound.get("ipa").getAsString();
                    var tags = extractTagString(sound);
                    String tagsText = (tags.isEmpty() ? "" : tags + " ");
                    ipas.add(tagsText + ipaText);
                }
            }

            if (!ipas.isEmpty()) {
                // Same headword can be in multiple etymologies and POS,
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

        /**
         * This method reads the {@code tags} and {@code raw_tags} from an object and returns
         * a list of their combined unique values separated by commas. It returns empty string if
         * no tags are present.
         * It was initialized designed to be used on {@code sound} objects, to extract extra
         * information about phonetic pronunciations or audio files.
         */
        private static String extractTagString(JsonObject obj) {
            var joined = Stream.of("tags", "raw_tags")
                    .filter(obj::has)
                    .map(obj::getAsJsonArray)
                    .flatMap(ja -> ja.asList().stream())
                    .map(JsonElement::getAsString)
                    .distinct()
                    .collect(Collectors.joining(", "));
            return joined.isEmpty() ? "" : "«" + joined + "»";
        }

        private void processSensesInternal(long headwordId, String pos, String posTitle, JsonArray senses, String word)
                throws SQLException {
            int senseIdx = headwordSenseCounter.getOrDefault(word, 0);
            for (JsonElement senseElem : senses) {
                JsonObject sense = senseElem.getAsJsonObject();
                long entryId = insertLexicalEntryInternal(headwordId, pos, posTitle, senseIdx++);
                processGlossesInternal(entryId, sense);
                processLinksInternal(entryId, sense);
                processExamplesInternal(entryId, sense);
                processRelationsInternal(entryId, sense);
            }
            headwordSenseCounter.put(word, senseIdx);
        }

        private long insertLexicalEntryInternal(long headwordId, String pos, String posTitle, int index) throws SQLException {
            pEntry.setLong(1, headwordId);
            pEntry.setString(2, pos);
            pEntry.setString(3, posTitle);
            pEntry.setInt(4, index);
            pEntry.executeUpdate();
            return getGeneratedKeys(pEntry);
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
            // Give priority to form_of links, as they are simpler: no anchor, no potential to
            // refer to the wrong word as the text word, present in every language dump
            if (sense.has("form_of")) {
                for (JsonElement formOfElem : sense.getAsJsonArray("form_of")) {
                    JsonObject formOfObj = formOfElem.getAsJsonObject();
                    if (formOfObj.has("word")) {
                        var word = formOfObj.get("word").getAsString();
                        pSenseLink.setLong(1, entryId);
                        pSenseLink.setString(2, word);
                        pSenseLink.setLong(3, getOrCreateHeadwordIdInternal(word));
                        pSenseLink.addBatch();
                        formOfCount++;
                    }
                }
            }
            // As a backup, process links: English only, and they need anchors to be of value
            // (otherwise they are referring to the same language of the dump)
            // Also, the unique key on the links table will ensure that if there is both a link and
            // a form_of of the same word, the link insertion will be ignored
            if (sense.has("links")) {
                // Are these only available in the English Wiktionary?
                for (JsonElement linkElem : sense.getAsJsonArray("links")) {
                    JsonArray linkArr = linkElem.getAsJsonArray();
                    if (linkArr.size() == 2) {
                        String target = linkArr.get(1).getAsString();
                        // Checking that the anchor is to the learning language seems unreliable,
                        // so add anyway and clean up later
                        String[] splitTarget = target.split("#");
                        if (splitTarget.length == 2) {
                            String lemma = splitTarget[0];
                            pSenseLink.setLong(1, entryId);
                            pSenseLink.setString(2, linkArr.get(0).getAsString());
                            pSenseLink.setLong(3, getOrCreateHeadwordIdInternal(lemma));
                            pSenseLink.addBatch();
                            linkCount++;
                        }
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
                    try {
                        pExample.executeUpdate();
                    } catch (SQLException e) {
                        // Some entries happen to have multiple repeated examples, just ignore them
                        continue;
                    }
                    long exId;
                    exId = getGeneratedKeys(pExample);
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
            pHeadword.executeUpdate();
            long id = getGeneratedKeys(pHeadword);
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

        private void cleanDeadEnds() throws SQLException {
            // First, there are lexical entries without definitions (glosses), stuff like this:
            // {"word": "waste", "lang_code": "en", "pos_title": "Substantivo", "senses": [{"tags": ["no-gloss"]}]}
            deletedLexicalEntries = conn.prepareStatement("""
                    DELETE FROM lexical_entries
                    WHERE id NOT IN (
                        SELECT g.lexical_entry_id
                        FROM glosses g
                    )
                    """).executeUpdate();

            // Links referring to headwords with no lex categories (or anything else) are either
            // really empty records (like those above) or a new headword for a link that is broken
            // or from another language (lots), therefore erase these headwords and these links

            deletedLinks = conn.prepareStatement("""
                    DELETE FROM sense_links
                    WHERE native_headword_id IN (
                        SELECT h.id
                        FROM headwords h
                        LEFT JOIN lexical_entries le ON h.id = le.headword_id
                        WHERE le.id is null
                    )
                    """).executeUpdate();

            // Either by having no more dead links or deleted empty lexical entries,
            // get rid of those useless headwords
            deletedHeadwords = conn.prepareStatement("""
                    DELETE FROM headwords
                    WHERE id NOT IN (
                        SELECT le.headword_id
                        FROM lexical_entries le
                    )
                    """).executeUpdate();

            // Obtain the respective counters, especially links, as it is not the same as
            // links plus form_of minus deleted because of ignored links as they conflicted
            // with form_of and got ignored but still counted
            headwordCount = selectLong("SELECT count(*) FROM headwords");
            collectiveLinkCount = selectLong("SELECT count(*) FROM sense_links");

            conn.commit();
        }

        private long selectLong(String query) throws SQLException {
            var ps = conn.prepareStatement(query);
            if (!ps.execute()) {
                throw new RuntimeException("Failed to execute");
            }
            var rs = ps.getResultSet();
            if (!rs.next()) {
                throw new RuntimeException("No value");
            }
            return rs.getLong(1);
        }

        private long getGeneratedKeys(PreparedStatement ps) throws SQLException {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new RuntimeException("No value");
                }
                return rs.getLong(1);
            }
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
        if (args.length < 4) {
            System.out.println(
                    "Usage: java KaikkiProcessor <version> <num_threads> <mirrors_csv> <nativeLang1:last_mod:dumpPath1> ...");
            return;
        }

        Instant totalStart = Instant.now();
        long epochSeconds = totalStart.getEpochSecond();
        String version = args[0];
        int numThreads = Integer.parseInt(args[1]);
        String[] mirrorBases = args[2].isEmpty() ? new String[0] : args[2].split(",");
        String timestampDir = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

        // Find the absolute root of the project to ensure output goes to project-root/processor/out/
        String currentDir = System.getProperty("user.dir");
        String outBase = currentDir.endsWith("processor") ? "out" : "processor/out";
        String outDir = outBase + File.separator + timestampDir + File.separator + epochSeconds;
        new File(outDir).mkdirs();

        KaikkiProcessor converter = new KaikkiProcessor();
        Map<String, Map<String, Boolean>> summaryTable = new TreeMap<>();
        List<Map<String, Object>> dictionaryMetadataList = new ArrayList<>();

        int totalDumps = args.length - 3;
        for (int i = 3; i < args.length; i++) {
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
            System.out.printf(
                    Locale.US,
                    "\nStarting pass of dump: %s (Native: %s, %s, Mod: %s) with %d threads%n",
                    dumpPath,
                    nativeLangCode,
                    nativeLangName,
                    lastModInstant,
                    numThreads);

            Instant dumpStart = Instant.now();
            Map<String, DatabaseSession> sessions = converter.processDumpParallel(
                    dumpPath,
                    nativeLangCode,
                    outDir,
                    i - 3,
                    totalDumps,
                    numThreads,
                    version,
                    lastModEpoch);

            for (Map.Entry<String, DatabaseSession> entry : sessions.entrySet()) {
                String learningCode = entry.getKey();
                DatabaseSession session = entry.getValue();
                boolean kept = !session.removed;
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
                    meta.put("size_bytes", dbFile.length());
                    meta.put("headwords", session.headwordCount);
                    meta.put("glosses", session.glossCount);
                    meta.put("examples", session.exampleCount);
                    meta.put("pronunciations", session.pronunciationCount);
                    meta.put("links", session.collectiveLinkCount);
                    dictionaryMetadataList.add(meta);
                }
            }

            Duration elapsed = Duration.between(dumpStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
            System.out.printf(Locale.US, "Dump %s processed in %s%n", dumpPath, elapsed);
        }

        writeMetadataJson(outDir, dictionaryMetadataList, epochSeconds, version, mirrorBases);
        printSummaryTable(summaryTable);

        Duration totalElapsed = Duration.between(totalStart, Instant.now()).truncatedTo(ChronoUnit.SECONDS);
        System.out.printf(Locale.US, "Entire process finished in %s%n", totalElapsed);
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
            String[] mirrorPairs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", epochSeconds);
        root.put(
                "timestamp_iso",
                Instant.ofEpochSecond(epochSeconds)
                        .atZone(ZoneOffset.systemDefault())
                        .toOffsetDateTime()
                        .toString());

        Map<String, String> fullMirrors = new LinkedHashMap<>();
        for (String pair : mirrorPairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new RuntimeException("Invalid mirror format (expected ID=URL): " + pair);
            }
            String id = parts[0].trim();
            String base = parts[1].trim();

            String fullUrl;
            if ("pCloud".equalsIgnoreCase(id)) {
                fullUrl = base + (base.endsWith("/") ? "" : "/") + "QuickCard4AnkiDroid/" + version + "/" + epochSeconds;
            } else if ("BackBlaze".equalsIgnoreCase(id)) {
                fullUrl =
                        base + (base.endsWith("/") ? "" : "/") + "QuickCard4AnkiDroid-" + version + "-" + epochSeconds;
            } else {
                throw new RuntimeException("Unknown mirror ID: " + id);
            }
            fullMirrors.put(id, fullUrl);
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
            String outDir,
            int dumpIndex,
            int totalDumps,
            int numThreads,
            String version,
            long lastModEpoch) throws SQLException, IOException, InterruptedException {
        Map<String, DatabaseSession> sessions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Semaphore semaphore = new Semaphore(numThreads * 2);
        AtomicLong linesCount = new AtomicLong(0);
        AtomicLong unprocessableLinesCount = new AtomicLong(0);
        AtomicLong selfLangLinesCount = new AtomicLong(0);
        AtomicLong fringeLinesCount = new AtomicLong(0);

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
                        if (entryLangCode == null) {
                            unprocessableLinesCount.incrementAndGet();
                        } else if (entryLangCode.length() != 2) {
                            fringeLinesCount.incrementAndGet();
                        } else if (entryLangCode.equals(nativeLangCode)) {
                            selfLangLinesCount.incrementAndGet();
                        } else {
                            sessions.computeIfAbsent(entryLangCode, code -> {
                                String dbFileName = String.format(Locale.US,
                                        "wiktionary_kaikki_%s-%s_%s_%d.db", code, nativeLangCode, version, lastModEpoch);
                                try {
                                    return new DatabaseSession(outDir + File.separator + dbFileName, code);
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to create database", e);
                                }
                            }).queue.put(entry);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing line: " + e);
                    } finally {
                        semaphore.release();
                    }
                    long current = linesCount.incrementAndGet();
                    if (current % 10000 == 0) {
                        System.out.printf(
                                Locale.US,
                                "\r[%d/%d] %s : processed %d lines...",
                                dumpIndex + 1,
                                totalDumps,
                                nativeLangCode,
                                current);
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            System.out.printf(
                    Locale.US,
                    "\r[%d/%d] %s : processed %d lines (%d unprocessable - %.2f%%, %d %s - %.2f%%, %d fringe - %.2f%%)... Done.",
                    dumpIndex + 1,
                    totalDumps,
                    nativeLangCode,
                    linesCount.get(),
                    unprocessableLinesCount.get(),
                    100.0 * unprocessableLinesCount.get() / linesCount.get(),
                    selfLangLinesCount.get(),
                    nativeLangCode,
                    100.0 * selfLangLinesCount.get() / linesCount.get(),
                    fringeLinesCount.get(),
                    100.0 * fringeLinesCount.get() / linesCount.get());

            System.out.printf("%nSummary for dump: %s%n", dumpPath);
            for (Map.Entry<String, DatabaseSession> entry : new TreeMap<>(sessions).entrySet()) {
                DatabaseSession session = entry.getValue();
                session.close();
                String learningCode = entry.getKey();
                if (session.headwordCount < MIN_HEADWORDS) {
                    new File(session.dbPath).delete();
                    session.removed = true;
                }
                System.out.printf(
                        Locale.US,
                        "%s %s, %s: %d headwords (without %d dead ends), %d glosses (without %d empty lexical entries), %d examples, %d pronunciations, %d links (%d links plus %d forms minus %d dead ends).%s%n",
                        session.removed ? " ! " : ">>>",
                        learningCode,
                        session.learningLangName,
                        session.headwordCount,
                        session.deletedHeadwords,
                        session.glossCount,
                        session.deletedLexicalEntries,
                        session.exampleCount,
                        session.pronunciationCount,
                        session.collectiveLinkCount,
                        session.linkCount,
                        session.formOfCount,
                        session.deletedLinks,
                        session.removed ? " Deleted by being below " + MIN_HEADWORDS + " headwords." : "");
            }
        }
        return sessions;
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
        st.execute("""
                CREATE TABLE headwords (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    headword TEXT UNIQUE NOT NULL,
                    ipa TEXT)""");
        st.execute("""
                CREATE TABLE lexical_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    headword_id INTEGER NOT NULL,
                    pos TEXT NOT NULL,
                    pos_title TEXT NOT NULL,
                    sense_index INTEGER NOT NULL,
                    FOREIGN KEY(headword_id) REFERENCES headwords(id),
                    UNIQUE(headword_id, pos, pos_title, sense_index))""");
        st.execute("""
                CREATE TABLE glosses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lexical_entry_id INTEGER NOT NULL,
                    gloss TEXT NOT NULL,
                    gloss_index INTEGER NOT NULL,
                    FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id),
                    UNIQUE(lexical_entry_id, gloss, gloss_index))""");
        st.execute("""
                CREATE TABLE examples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lexical_entry_id INTEGER NOT NULL,
                    learning_text TEXT NOT NULL,
                    native_text TEXT NOT NULL,
                    FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id),
                    UNIQUE(lexical_entry_id, learning_text, native_text))""");
        st.execute("""
                CREATE TABLE sense_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lexical_entry_id INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    native_headword_id INTEGER NOT NULL,
                    FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id),
                    FOREIGN KEY(native_headword_id) REFERENCES headwords(id),
                    UNIQUE(lexical_entry_id, word))""");
        st.execute("""
                CREATE TABLE bold_offsets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    example_id INTEGER NOT NULL,
                    text_type CHAR(1) NOT NULL,
                    start_index INTEGER NOT NULL,
                    end_index INTEGER NOT NULL,
                    FOREIGN KEY(example_id) REFERENCES examples(id),
                    UNIQUE(example_id, text_type, start_index, end_index))""");
        st.execute("""
                CREATE TABLE pronunciations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    headword_id INTEGER NOT NULL,
                    audio_url TEXT NOT NULL,
                    description TEXT,
                    FOREIGN KEY(headword_id) REFERENCES headwords(id),
                    UNIQUE(headword_id, audio_url))""");
        st.execute("""
                CREATE TABLE relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lexical_entry_id INTEGER NOT NULL,
                    type CHAR(1) NOT NULL,
                    word TEXT NOT NULL,
                    FOREIGN KEY(lexical_entry_id) REFERENCES lexical_entries(id),
                    UNIQUE(lexical_entry_id, type, word))""");
        st.execute("""
                CREATE TABLE languages (
                    iso TEXT PRIMARY KEY,
                    name TEXT NOT NULL)""");
        st.execute("CREATE INDEX idx_hw_word ON headwords(headword)");
        st.execute("CREATE INDEX idx_le_hw ON lexical_entries(headword_id)");
        st.execute("CREATE INDEX idx_gl_le ON glosses(lexical_entry_id)");
        st.execute("CREATE INDEX idx_ex_le ON examples(lexical_entry_id)");
        st.execute("CREATE INDEX idx_sl_le ON sense_links(lexical_entry_id)");
    }

    private static void printSummaryTable(Map<String, Map<String, Boolean>> status) {
        if (status.isEmpty()) return;
        Set<String> natives = new TreeSet<>();
        for (Map<String, Boolean> map : status.values()) natives.addAll(map.keySet());
        System.out.println("\n--- EXTRACTION SUMMARY TABLE ---");
        System.out.print("lrn\\nat |");
        for (String t : natives) {
            System.out.printf(" %-4s |", t);
        }
        System.out.println();
        for (Map.Entry<String, Map<String, Boolean>> entry : status.entrySet()) {
            System.out.printf(" %-6s |", entry.getKey());
            for (String t : natives) {
                Boolean kept = entry.getValue().get(t);
                System.out.print(kept != null && kept ? "  X   |" : "      |");
            }
            System.out.println();
        }
        System.out.println("--------------------------------\n");
    }
}
