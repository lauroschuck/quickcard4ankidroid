package com.github.lauroschuck.ankiquickadd;

import android.app.Application;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.github.lauroschuck.ankiquickadd.data.DatabaseRemoteStorage;
import com.github.lauroschuck.ankiquickadd.data.DictionaryRepository;
import com.github.lauroschuck.ankiquickadd.data.NavigationManager;
import com.github.lauroschuck.ankiquickadd.data.WordRepository;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import timber.log.Timber;

public class MainViewModel extends AndroidViewModel {
    private static final String KEY_METADATA = "downloaded_dictionary_metadata";

    @Getter
    private final DictionaryRepository dictionaryRepository;

    @Getter
    private final WordRepository wordRepository;

    @Getter
    private final NavigationManager navigationManager;

    @Getter
    private final DatabaseRemoteStorage databaseRemoteStorage;

    private final MutableLiveData<Integer> definitionSelectedCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> exampleSelectedCount = new MutableLiveData<>(0);

    private final MutableLiveData<List<DatabaseRemoteStorage.DictionaryStats>> allAvailableStats =
            new MutableLiveData<>(new ArrayList<>());

    public record DownloadedDictionary(Language learning, Language nativeLang, File file) {}

    private static final MutableLiveData<List<DownloadedDictionary>> downloadedDictionaries =
            new MutableLiveData<>(new ArrayList<>());

    private static final MutableLiveData<DownloadInfo> activeDownload = new MutableLiveData<>(null);

    @Setter
    @Getter
    private Language lastUsedLearningLanguage;

    @Setter
    @Getter
    private Language lastUsedNativeLanguage;

    public record DownloadInfo(String fileName, long downloaded, long total) {
        public int getProgress() {
            return total > 0 ? (int) (downloaded * 100 / total) : 0;
        }

        public String getMbText() {
            return String.format(Locale.US, "%.1f / %.1f MB", downloaded / 1000.0 / 1000.0, total / 1000.0 / 1000.0);
        }
    }

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.dictionaryRepository = new DictionaryRepository(application);
        this.wordRepository = new WordRepository(application);
        this.navigationManager = new NavigationManager();
        this.databaseRemoteStorage = new DatabaseRemoteStorage(application);
        refreshAvailableLanguages();
        refreshDownloadedDictionaries();
    }

    public void refreshAvailableLanguages() {
        databaseRemoteStorage.discoverAvailableLanguages(new DatabaseRemoteStorage.DiscoveryCallback() {
            @Override
            public void onSuccess(List<DatabaseRemoteStorage.DictionaryStats> stats) {
                allAvailableStats.postValue(stats);
                Timber.i("Stats refreshed: %d pairs found", stats.size());
            }

            @Override
            public void onError(String message) {
                Timber.w("Failed to refresh languages: %s", message);
            }
        });
    }

    public void refreshDownloadedDictionaries() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadataEntries = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));

        File dbDir = getApplication().getDatabasePath("fake.db").getParentFile();
        List<DownloadedDictionary> list = new ArrayList<>();

        if (dbDir != null && dbDir.exists()) {
            for (String entry : metadataEntries) {
                String[] parts = entry.split(":");
                if (parts.length == 3) {
                    try {
                        Language l = Language.ofIsoCode(parts[0]);
                        Language n = Language.ofIsoCode(parts[1]);
                        String fileName = parts[2];
                        File f = new File(dbDir, fileName);
                        if (f.exists()) {
                            list.add(new DownloadedDictionary(l, n, f));
                        }
                    } catch (RuntimeException e) {
                        Timber.w(e, "Failed to parse metadata entry: %s", entry);
                    }
                }
            }
        }

        downloadedDictionaries.postValue(list);

        if (list.size() == 1) {
            autoSelectDictionary(list.get(0));
        }
    }

    private void autoSelectDictionary(DownloadedDictionary dict) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        prefs.edit()
                .putString(
                        SettingsActivity.KEY_LEARNING_LANGUAGE, dict.learning().getIsoCode())
                .putString(
                        SettingsActivity.KEY_NATIVE_LANGUAGE, dict.nativeLang().getIsoCode())
                .apply();
        Timber.d(
                "Auto-selected dictionary: %s-%s",
                dict.learning().getIsoCode(), dict.nativeLang().getIsoCode());
    }

    public List<Language> getAvailableLearningLanguages() {
        List<DatabaseRemoteStorage.DictionaryStats> stats = allAvailableStats.getValue();
        if (stats == null) return new ArrayList<>();
        return stats.stream()
                .map(DatabaseRemoteStorage.DictionaryStats::learning)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Language> getAvailableNativeLanguages(Language learning) {
        List<DatabaseRemoteStorage.DictionaryStats> stats = allAvailableStats.getValue();
        if (stats == null || learning == null) {
            return new ArrayList<>();
        }
        return stats.stream()
                .filter(s -> s.learning().equals(learning))
                .map(DatabaseRemoteStorage.DictionaryStats::nativeLang)
                .distinct()
                .collect(Collectors.toList());
    }

    public DatabaseRemoteStorage.DictionaryStats getStatsFor(Language learning, Language nativeLang) {
        List<DatabaseRemoteStorage.DictionaryStats> stats = allAvailableStats.getValue();
        if (stats == null || learning == null || nativeLang == null) {
            return null;
        }
        return stats.stream()
                .filter(s -> s.learning().equals(learning) && s.nativeLang().equals(nativeLang))
                .findFirst()
                .orElse(null);
    }

    public void downloadDictionary(Language learning, Language nativeLang) {
        DatabaseRemoteStorage.DictionaryStats stats = getStatsFor(learning, nativeLang);
        if (stats == null) {
            Timber.e("Cannot download dictionary: stats not found for %s-%s", learning, nativeLang);
            return;
        }

        activeDownload.postValue(new DownloadInfo(stats.fileName(), 0, 0));

        databaseRemoteStorage.downloadDictionary(stats, new DatabaseRemoteStorage.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                activeDownload.postValue(new DownloadInfo(stats.fileName(), downloaded, total));
            }

            @Override
            public void onSuccess(File file, long elapsedMs) {
                activeDownload.postValue(null);
                FirebaseHelper.logDownloadDictionary(learning, nativeLang, elapsedMs);
                registerInMetadata(learning, nativeLang, file.getName());
                refreshDownloadedDictionaries();
                dictionaryRepository.reloadSources();
            }

            @Override
            public void onError(String errorMessage, Throwable throwable, long elapsedMs) {
                activeDownload.postValue(null);
                FirebaseHelper.logDownloadDictionary(learning, nativeLang, elapsedMs, errorMessage, throwable);
                Timber.e("Download failed: %s", errorMessage);
            }
        });
    }

    private void registerInMetadata(Language l, Language n, String fileName) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadata = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        String pairPrefix = l.getIsoCode() + ":" + n.getIsoCode() + ":";
        metadata.removeIf(s -> s.startsWith(pairPrefix));
        metadata.add(pairPrefix + fileName);
        prefs.edit().putStringSet(KEY_METADATA, metadata).apply();
    }

    public void deleteDictionary(DownloadedDictionary dict) {
        if (dict.file().delete()) {
            unregisterFromMetadata(dict.learning(), dict.nativeLang());
            clearPrefsIfMatches(dict.learning(), dict.nativeLang());
            refreshDownloadedDictionaries();
            dictionaryRepository.reloadSources();
        }
    }

    private void unregisterFromMetadata(Language l, Language n) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadata = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        String pairPrefix = l.getIsoCode() + ":" + n.getIsoCode() + ":";
        metadata.removeIf(s -> s.startsWith(pairPrefix));
        prefs.edit().putStringSet(KEY_METADATA, metadata).apply();
    }

    private void clearPrefsIfMatches(Language learning, Language nativeLang) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        String curL = prefs.getString(SettingsActivity.KEY_LEARNING_LANGUAGE, "");
        String curN = prefs.getString(SettingsActivity.KEY_NATIVE_LANGUAGE, "");
        if (learning.getIsoCode().equals(curL) && nativeLang.getIsoCode().equals(curN)) {
            prefs.edit()
                    .remove(SettingsActivity.KEY_LEARNING_LANGUAGE)
                    .remove(SettingsActivity.KEY_NATIVE_LANGUAGE)
                    .apply();
            Timber.d(
                    "Cleared selected dictionary as it was deleted: %s-%s",
                    learning.getIsoCode(), nativeLang.getIsoCode());
        }
    }

    public LiveData<List<DatabaseRemoteStorage.DictionaryStats>> getObservableStats() {
        return allAvailableStats;
    }

    public LiveData<List<DownloadedDictionary>> getDownloadedDictionaries() {
        return downloadedDictionaries;
    }

    public LiveData<DownloadInfo> getActiveDownload() {
        return activeDownload;
    }

    public LiveData<Integer> getDefinitionSelectedCount() {
        return definitionSelectedCount;
    }

    public void setDefinitionSelectedCount(int count) {
        definitionSelectedCount.setValue(count);
    }

    public LiveData<Integer> getExampleSelectedCount() {
        return exampleSelectedCount;
    }

    public void setExampleSelectedCount(int count) {
        exampleSelectedCount.setValue(count);
    }

    public void markWordAsProcessed(String word) {
        wordRepository.markWordAsProcessed(word);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dictionaryRepository.close();
    }
}
