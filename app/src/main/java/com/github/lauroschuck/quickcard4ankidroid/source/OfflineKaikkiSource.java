package com.github.lauroschuck.quickcard4ankidroid.source;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.text.TextUtils;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.AbstractAnkiNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.DictionaryNote;
import com.github.lauroschuck.quickcard4ankidroid.anki.notes.TextNote;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainViewModel;
import com.github.lauroschuck.quickcard4ankidroid.util.ResourcesUtil;
import com.github.lauroschuck.quickcard4ankidroid.util.UiUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import timber.log.Timber;

public class OfflineKaikkiSource implements DataSource {
    private Context context;
    private Template template;
    private String extractionJs;
    private Language lastLearningLanguage;
    private Language lastNativeLanguage;
    private SQLiteDatabase currentDb;
    private String currentDbName;

    @Override
    public String getName() {
        return "Wiktionary";
    }

    @Override
    public void setContext(@NonNull Context context) {
        this.context = context;
        try {
            this.template = new Handlebars().compileInline(ResourcesUtil.loadRaw(R.raw.source_kaikki_view, context));
            this.extractionJs = ResourcesUtil.loadRaw(R.raw.source_kaikki_js, context);
        } catch (IOException e) {
            throw new RuntimeException("Handlebars compilation failed", e);
        }
    }

    @Override
    public void close() {
        if (currentDb != null) {
            Timber.d("Closing offline database: %s", currentDbName);
            currentDb.close();
            currentDb = null;
            currentDbName = null;
        }
    }

    private SQLiteDatabase getDatabase(Language learningLanguage, Language nativeLanguage) {
        var dbName = MainViewModel.getDbNameFromMetadata(context, learningLanguage, nativeLanguage);
        if (dbName == null) {
            var msg = String.format(
                    "No metadata found for %s-%s dictionary",
                    learningLanguage.getIsoCode(), nativeLanguage.getIsoCode());
            Timber.e(msg);
            throw new DataSourceException(msg);
        }

        if (currentDb != null && dbName.equals(currentDbName)) {
            return currentDb;
        }

        close();

        var dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            Timber.e("Database file not found: %s", dbFile.getAbsolutePath());
            throw new DataSourceException(String.format(
                    "File not found for %s-%s database", learningLanguage.getIsoCode(), nativeLanguage.getIsoCode()));
        }

        try {
            currentDb = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            currentDbName = dbName;
            Timber.d("Opened offline database: %s", dbName);
            return currentDb;
        } catch (SQLiteException e) {
            Timber.e(e, "Failed to open database: %s", dbName);
            throw new DataSourceException(
                    String.format(
                            "Failed to open %s-%s database",
                            learningLanguage.getIsoCode(), nativeLanguage.getIsoCode()),
                    e);
        }
    }

    @Override
    public void fetch(
            @NonNull String word,
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            @NonNull OnResultListener listener) {
        this.lastLearningLanguage = learningLanguage;
        this.lastNativeLanguage = nativeLanguage;

        try {
            var db = getDatabase(learningLanguage, nativeLanguage);

            var data = new HashMap<String, Object>();
            data.put("word", word);
            data.put("maxSenses", DictionaryNote.DEFINITION_FIELDS);

            // Inject theme colors
            Map<String, String> colors = new HashMap<>();
            colors.put("text_primary", UiUtil.colorToHex(context, R.color.text_primary));
            colors.put("text_secondary", UiUtil.colorToHex(context, R.color.text_secondary));
            colors.put("accent_primary", UiUtil.colorToHex(context, R.color.accent_primary));
            colors.put("divider_color", UiUtil.colorToHex(context, R.color.divider_color));
            colors.put("item_background_alt", UiUtil.colorToHex(context, R.color.item_background_alt));
            colors.put("app_background", UiUtil.colorToHex(context, R.color.app_background));
            data.put("colors", colors);

            // 0. Fetch language name for Wiktionary anchor and construct URL
            var langName = getLanguageName(db, learningLanguage);
            data.put("langName", langName);
            data.put(
                    "wiktionaryUrl",
                    assembleWiktionaryLink(word, nativeLanguage, langName).toString());

            // 1. Fetch IPA
            String ipa = null;
            try (var cursor = db.rawQuery("SELECT ipa FROM headwords WHERE headword = ?", new String[] {word})) {
                if (cursor.moveToFirst()) {
                    ipa = cursor.getString(0);
                }
            }
            data.put("ipa", ipa);

            // 2. Fetch Pronunciations
            var pronunciations = new ArrayList<Map<String, String>>();
            var pronQuery =
                    "SELECT audio_url, description FROM pronunciations p JOIN headwords h ON p.headword_id = h.id WHERE h.headword = ? COLLATE BINARY";
            try (var cursor = db.rawQuery(pronQuery, new String[] {word})) {
                while (cursor.moveToNext()) {
                    Map<String, String> pron = new HashMap<>();
                    String url = cursor.getString(0);
                    String desc = cursor.getString(1);
                    pron.put("url", url);
                    pron.put("encodedUrl", URLEncoder.encode(url, "UTF-8"));
                    pron.put(
                            "description",
                            desc != null && !desc.isEmpty() ? desc : "<span class='no-desc'>No description</span>");
                    pronunciations.add(pron);
                }
            }
            data.put("pronunciations", pronunciations);
            data.put("hasPronunciation", (ipa != null && !ipa.isEmpty()) || !pronunciations.isEmpty());
            Timber.d("Found %d pronunciations", pronunciations.size());

            // 3. Fetch variations (other casings)
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

            // 4. Fetch Lexical Entries and nest everything
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
            Timber.d("Found %d POS blocks", posBlocks.size());

            if (posBlocks.isEmpty()) {
                // If word not found exactly, but we have variations, show them in a special "not found" page
                if (!variations.isEmpty()) {
                    var html = template.apply(data);
                    listener.onSuccess(html, word);
                } else {
                    listener.onNotFound();
                }
                return;
            }

            var html = template.apply(data);
            Timber.d("Generated HTML length: %d", html.length());
            listener.onSuccess(html, word);

        } catch (DataSourceException e) {
            listener.onError("Database error: " + e.getMessage(), e);
        } catch (IOException e) {
            Timber.e(e, "Template processing failed");
            listener.onError("Page construction error", e);
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

    private static Uri assembleWiktionaryLink(
            @NonNull String headword, @NonNull Language nativeLanguage, @NonNull String anchor) {
        try {
            var encodedWord = URLEncoder.encode(headword, "UTF-8");
            var encodedAnchor = URLEncoder.encode(anchor.replace(" ", "_"), "UTF-8");
            return Uri.parse(String.format(
                    "https://%s.wiktionary.org/wiki/%s#%s",
                    nativeLanguage.getIsoCode().toLowerCase(), encodedWord, encodedAnchor));
        } catch (UnsupportedEncodingException | RuntimeException e) {
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
                // Use Unicode-aware regex lookarounds to match only standalone words
                // (?<!\p{L}) : not preceded by a letter
                // (?!\p{L})  : not followed by a letter
                String regex = "(?<!\\p{L})" + Pattern.quote(linkText) + "(?!\\p{L})";
                text = text.replaceAll(regex, "<a href='app://fetch/" + nativeWord + "'>$0</a>");
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
        return extractionJs;
    }

    @Override
    public SelectedCards<? extends AbstractAnkiNote.Input> getCardsFromSelection(@NonNull String json) {
        try {
            var obj = new Gson().fromJson(json, JsonObject.class);
            var mode = obj.has("mode") ? obj.get("mode").getAsString() : "examples";
            var selectedAudio = obj.has("audioUrl") && !obj.get("audioUrl").isJsonNull()
                    ? Uri.parse(obj.get("audioUrl").getAsString())
                    : null;

            if (lastLearningLanguage == null || lastNativeLanguage == null) {
                throw new IllegalArgumentException(
                        String.format("No language pair: %s-%s", lastLearningLanguage, lastNativeLanguage));
            }

            var db = getDatabase(lastLearningLanguage, lastNativeLanguage);

            if (mode.equals("examples")) {
                var exampleIds = obj.getAsJsonArray("examples");
                var cards = new ArrayList<TextNote.Input>();
                String headword = null;
                for (var idElem : exampleIds) {
                    var card = fetchCardForExample(db, idElem.getAsLong());
                    headword = card.headword();
                    cards.add(card);
                }
                var sourceUrl =
                        assembleWiktionaryLink(headword, lastNativeLanguage, getLanguageName(db, lastLearningLanguage));
                return new SelectedTextCards(lastLearningLanguage, lastNativeLanguage, selectedAudio, sourceUrl, cards);
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
                var firstCard = cards.stream().findFirst().orElseThrow();
                var headword = firstCard.headword();
                var sourceUrl =
                        assembleWiktionaryLink(headword, lastNativeLanguage, getLanguageName(db, lastLearningLanguage));
                return new SelectedDictionaryCards(
                        lastLearningLanguage, lastNativeLanguage, selectedAudio, sourceUrl, cards);
            }
        } catch (RuntimeException e) {
            Timber.e(e, "Error generating cards from selection");
            throw e;
        }
    }

    private TextNote.Input fetchCardForExample(SQLiteDatabase db, long exId) {
        var query = "SELECT e.learning_text, e.native_text, le.lexical_category, h.headword, h.ipa, e.lexical_entry_id "
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
            var ipa = cursor.getString(4);
            var entryId = cursor.getLong(5);

            var learningText = applyBolding(learningTextRaw, exId, "L", db);
            var nativeText = nativeTextRaw != null ? applyBolding(nativeTextRaw, exId, "N", db) : null;
            var glosses = fetchGlosses(db, entryId);

            return new TextNote.Input(headword, ipa, learningText, nativeText, glosses, lexicalCategory);
        }
    }

    private List<DictionaryNote.Input> fetchCardForDefinition(SQLiteDatabase db, Map<Long, Long> entryMap) {
        var examples = entryMap.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
        var query = String.format(
                """
            SELECT le.id, h.headword, h.ipa, le.lexical_category, e.id, e.learning_text, e.native_text
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

        var flattenedCards = new ArrayList<DictionaryNote.Input>();
        try (var cursor = db.rawQuery(query, arguments)) {
            while (cursor.moveToNext()) {
                var entryId = cursor.getLong(0);
                var headword = cursor.getString(1);
                var ipa = cursor.getString(2);
                var lexicalCategory = cursor.getString(3);
                var definitionText = fetchGlosses(db, entryId);
                DictionaryNote.Input.Definition definition;
                if (cursor.isNull(4)) {
                    definition = new DictionaryNote.Input.Definition(definitionText, null, null);
                } else {
                    var exampleId = cursor.getLong(4);
                    var learningText = applyBolding(cursor.getString(5), exampleId, "L", db);
                    var nativeTextRaw = cursor.getString(6);
                    var nativeText = nativeTextRaw != null ? applyBolding(nativeTextRaw, exampleId, "N", db) : null;
                    definition = new DictionaryNote.Input.Definition(definitionText, learningText, nativeText);
                }
                var input = new DictionaryNote.Input(headword, ipa, lexicalCategory, List.of(definition));
                Timber.d("Intermediary input: %s", input);
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
                    var input = new DictionaryNote.Input(
                            firstInput.headword(), firstInput.ipa(), firstInput.lexicalCategory(), definitions);
                    Timber.d("Condensed input: %s", input);
                    return input;
                })
                .collect(Collectors.toList());
    }

    private static String createPlaceholderString(int size) {
        var placeholders = new String[size];
        Arrays.fill(placeholders, "?");
        return "(" + TextUtils.join(",", placeholders) + ")";
    }

    private String fetchGlosses(SQLiteDatabase db, long entryId) {
        var glosses = new StringBuilder();
        var glossQuery = "SELECT gloss FROM glosses WHERE lexical_entry_id = ? ORDER BY gloss_index";
        try (var glossCursor = db.rawQuery(glossQuery, new String[] {String.valueOf(entryId)})) {
            while (glossCursor.moveToNext()) {
                if (glosses.length() > 0) {
                    glosses.append("<br/>");
                }
                glosses.append(glossCursor.getString(0));
            }
        }
        return glosses.toString();
    }
}
