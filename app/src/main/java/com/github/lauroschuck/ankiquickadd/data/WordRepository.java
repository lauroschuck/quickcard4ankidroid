package com.github.lauroschuck.ankiquickadd.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.github.lauroschuck.ankiquickadd.EnqueueActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import timber.log.Timber;

public class WordRepository {
    private static final String KEY_PROCESSED_WORDS = "processed_words";

    private final SharedPreferences prefs;
    private final MutableLiveData<List<String>> enqueuedWords = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Set<String>> processedWords = new MutableLiveData<>(new HashSet<>());

    public WordRepository(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        loadPersistentData();
    }

    private void loadPersistentData() {
        Set<String> enqueuedSet = prefs.getStringSet(EnqueueActivity.KEY_ENQUEUED_WORDS, new HashSet<>());
        enqueuedWords.setValue(new ArrayList<>(enqueuedSet));

        Set<String> processedSet = prefs.getStringSet(KEY_PROCESSED_WORDS, new HashSet<>());
        processedWords.setValue(processedSet);
        Timber.d("Loaded persistent data: %d enqueued, %d processed", enqueuedSet.size(), processedSet.size());
    }

    public LiveData<List<String>> getEnqueuedWords() {
        return enqueuedWords;
    }

    public void removeEnqueuedWord(String word) {
        List<String> current = enqueuedWords.getValue();
        if (current != null) {
            List<String> updated = new ArrayList<>(current);
            if (updated.remove(word)) {
                enqueuedWords.setValue(updated);
                saveEnqueuedWords(updated);
                Timber.d("Removed word from enqueue: %s", word);
            }
        }
    }

    private void saveEnqueuedWords(List<String> words) {
        prefs.edit()
                .putStringSet(EnqueueActivity.KEY_ENQUEUED_WORDS, new HashSet<>(words))
                .apply();
    }

    public LiveData<Set<String>> getProcessedWords() {
        return processedWords;
    }

    public void markWordAsProcessed(String word) {
        String cleanWord = word.toLowerCase().trim();
        Set<String> current = processedWords.getValue();
        if (current == null) current = new HashSet<>();
        Set<String> updated = new HashSet<>(current);
        if (updated.add(cleanWord)) {
            processedWords.postValue(updated);
            prefs.edit().putStringSet(KEY_PROCESSED_WORDS, updated).apply();
            Timber.d("Marked word as processed: %s", cleanWord);
        }
    }

    public void reload() {
        loadPersistentData();
    }
}
