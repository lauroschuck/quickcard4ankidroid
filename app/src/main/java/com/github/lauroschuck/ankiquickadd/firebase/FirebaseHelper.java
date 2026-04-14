package com.github.lauroschuck.ankiquickadd.firebase;

import android.content.Context;
import android.os.Bundle;
import com.github.lauroschuck.ankiquickadd.anki.notes.DictionaryNote;
import com.github.lauroschuck.ankiquickadd.anki.notes.TextNote;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.NonNull;

public class FirebaseHelper {

    private static FirebaseAnalytics analytics;
    private static FirebaseCrashlytics crashlytics;
    private static FirebaseFirestore firestore;

    public static void init(Context context) {
        analytics = FirebaseAnalytics.getInstance(context);
        crashlytics = FirebaseCrashlytics.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public static void setUserLanguages(Language learning, Language nativeLang) {
        if (analytics == null) {
            return;
        }
        var crashlytics = FirebaseCrashlytics.getInstance();
        if (learning != null) {
            analytics.setUserProperty("learning_language", learning.getIsoCode());
            crashlytics.setCustomKey("learning_language", learning.getIsoCode());
        }
        if (nativeLang != null) {
            analytics.setUserProperty("native_language", nativeLang.getIsoCode());
            crashlytics.setCustomKey("native_language", nativeLang.getIsoCode());
        }
    }

    public static void logSearch(@NonNull String word, @NonNull SearchMethod method) {
        Bundle bundle = new Bundle();
        // TODO consider removing headword in the future, too high cardinality
        bundle.putString("headword", word);
        bundle.putString("search_method", method.value());
        logEvent("search_word", bundle);
    }

    public static void logFetchDefinition(@NonNull String word, boolean success) {
        Bundle bundle = new Bundle();
        bundle.putString("headword", word);
        bundle.putBoolean("success", success);
        logEvent("fetch_definition", bundle);
    }

    public static void logEnqueueWord(@NonNull String word) {
        Bundle bundle = new Bundle();
        bundle.putString("headword", word);
        logEvent("enqueue_word", bundle);
    }

    public static void logRemovedEnqueuedWord(@NonNull String word, Boolean added) {
        Bundle bundle = new Bundle();
        bundle.putString("headword", word);
        bundle.putString("enqueued_added", added == null ? "unknown" : added.toString());
        logEvent("delete_enqueued_word", bundle);
    }

    public static void logChangeNoteType(@NonNull NoteType type) {
        Bundle bundle = new Bundle();
        bundle.putString("note_type", type.value());
        logEvent("change_note_type", bundle);
    }

    public static void logExportCards(@NonNull String headword, @NonNull NoteType type, int count) {
        Bundle bundle = new Bundle();
        bundle.putString("headword", headword);
        bundle.putString("note_type", type.value());
        bundle.putInt("count", count);
        logEvent("export_cards", bundle);
    }

    public static void logExportDictionaryCards(
            @NonNull String headword, @NonNull DictionarySource.SelectedDictionaryCards dictCards) {
        var inputs = dictCards.inputs();

        // 1. Count of lexical categories (one Input record per category)
        var lexicalCategoryCount = inputs.size();

        // 2. Total count of definitions across all categories
        var totalDefinitions =
                inputs.stream().mapToInt(i -> i.definitions().size()).sum();

        // 3. Average definitions per lexical category
        var avgDefsPerCategory = (double) totalDefinitions / lexicalCategoryCount;

        // 4. Count of available example phrase pairs
        // Assumes a pair is available if both learningText and nativeText are present
        var examplePairsCount = inputs.stream()
                .flatMap(i -> i.definitions().stream())
                .filter(DictionaryNote.Input.Definition::hasText)
                .count();

        // 5. Proportion of definitions that have example phrase pairs
        double percentageWithExamples = totalDefinitions == 0 ? 0 : (double) examplePairsCount / totalDefinitions;

        Bundle bundle = new Bundle();
        bundle.putString("headword", headword);
        bundle.putInt("lexical_category_count", lexicalCategoryCount);
        bundle.putInt("definitions_count", totalDefinitions);
        bundle.putDouble("avg_defs_per_lexical_category", avgDefsPerCategory);
        bundle.putInt("example_pairs_count", (int) examplePairsCount);
        bundle.putDouble("example_pairs_proportion", percentageWithExamples);
        logEvent("export_dictionary_cards", bundle);
    }

    public static void logExportTextCards(
            @NonNull String headword, @NonNull DictionarySource.SelectedTextCards textCards) {
        var inputs = textCards.inputs();

        // 1. Count of example pairs
        var examplePairsCount = inputs.size();

        // 2. Count of distinct definitions
        var totalDefinitions =
                inputs.stream().map(TextNote.Input::definition).distinct().count();

        // 3. Count of distinct lexical categories
        var lexicalCategoryCount =
                inputs.stream().map(TextNote.Input::lexicalCategory).distinct().count();

        // 4. Average definitions per lexical category
        double avgDefsPerCategory = lexicalCategoryCount == 0 ? 0 : (double) totalDefinitions / lexicalCategoryCount;

        // 5. Average number of example phrases per definition
        double avgExamplesPerDefinition = totalDefinitions == 0 ? 0 : (double) examplePairsCount / totalDefinitions;

        Bundle bundle = new Bundle();
        bundle.putString("headword", headword);
        bundle.putLong("lexical_category_count", lexicalCategoryCount);
        bundle.putLong("definitions_count", totalDefinitions);
        bundle.putDouble("avg_defs_per_lexical_category", avgDefsPerCategory);
        bundle.putInt("example_pairs_count", examplePairsCount);
        bundle.putDouble("avg_examples_per_definition", avgExamplesPerDefinition);
        logEvent("export_text_cards", bundle);
    }

    public static void logDownloadDictionary(@NonNull Language learning, @NonNull Language nativeLang, long elapsedMs) {
        logDownloadDictionary(learning, nativeLang, elapsedMs, null, null);
    }

    public static void logDownloadDictionary(
            @NonNull Language learning,
            @NonNull Language nativeLang,
            long elapsedMs,
            String errorMessage,
            Throwable throwable) {
        Bundle bundle = new Bundle();
        bundle.putString("download_learning_language", learning.getIsoCode());
        bundle.putString("download_native_language", nativeLang.getIsoCode());
        bundle.putLong("elapsed_ms", elapsedMs);
        bundle.putBoolean("success", errorMessage == null && throwable == null);
        bundle.putString("error_message", errorMessage);
        bundle.putString(
                "exception", throwable == null ? null : throwable.getClass().getName());
        logEvent("download_dictionary", bundle);
    }

    public static void logDeleteDictionary(@NonNull Language learning, @NonNull Language nativeLang) {
        Bundle bundle = new Bundle();
        bundle.putString("download_learning_language", learning.getIsoCode());
        bundle.putString("download_native_language", nativeLang.getIsoCode());
        logEvent("delete_dictionary", bundle);
    }

    public static void logFeedback(boolean success) {
        Bundle bundle = new Bundle();
        bundle.putBoolean("success", success);
        logEvent("send_feedback", bundle);
    }

    private static void logEvent(String name, Bundle bundle) {
        if (analytics != null) {
            analytics.logEvent(name, bundle);
            logExceptionBreadcrumb(String.format("Event: %s\nBundle: %s", name, bundle));
        }
    }

    public static void logExceptionBreadcrumb(@NonNull String message) {
        crashlytics.log(message);
    }

    public static void sendFeedback(
            @NonNull String name,
            @NonNull String email,
            @NonNull String messsage,
            @NonNull OnSuccessListener<? super DocumentReference> onSuccessListener,
            @NonNull OnFailureListener onFailureListener) {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("name", name);
        feedback.put("email", email);
        feedback.put("timestamp", FieldValue.serverTimestamp());
        feedback.put("message", messsage);
        feedback.put("app_version", com.github.lauroschuck.ankiquickadd.BuildConfig.VERSION_NAME);
        feedback.put("os_version", android.os.Build.VERSION.RELEASE);
        feedback.put("device_model", android.os.Build.MODEL);

        firestore
                .collection("feedback")
                .add(feedback)
                .addOnSuccessListener(onSuccessListener)
                .addOnFailureListener(onFailureListener);
    }

    public enum SearchMethod {
        MANUAL,
        CONTEXT_MENU,
        ENQUEUED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum NoteType {
        DICTIONARY,
        TEXT;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
