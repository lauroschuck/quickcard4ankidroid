package com.github.lauroschuck.quickcard4ankidroid.ui.main;

import android.app.Application;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;
import com.github.lauroschuck.quickcard4ankidroid.data.DataSourceRepository;
import com.github.lauroschuck.quickcard4ankidroid.data.DatabaseRemoteStorage;
import com.github.lauroschuck.quickcard4ankidroid.data.NavigationManager;
import com.github.lauroschuck.quickcard4ankidroid.data.WordRepository;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.ui.settings.SettingsActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final DataSourceRepository dataSourceRepository;

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

    private final MutableLiveData<String> downloadError = new MutableLiveData<>(null);

    public record DownloadedDictionary(
            DatabaseRemoteStorage.DictionaryStats localStats, boolean updateAvailable, boolean isLegacy) {

        public Language learning() {
            return localStats.learning();
        }

        public Language nativeLang() {
            return localStats.nativeLang();
        }

        public File file(Context context) {
            return context.getDatabasePath(localStats.fileName());
        }
    }

    private static final MutableLiveData<List<DownloadedDictionary>> downloadedDictionaries =
            new MutableLiveData<>(new ArrayList<>());

    private static final MutableLiveData<DownloadInfo> activeDownload = new MutableLiveData<>(null);

    @Setter
    @Getter
    private Language lastUsedLearningLanguage;

    @Setter
    @Getter
    private Language lastUsedNativeLanguage;

    @Keep
    public record DownloadInfo(Language learning, Language nativeLang, String fileName, long downloaded, long total) {
        public int getProgress() {
            return total > 0 ? (int) (downloaded * 100 / total) : 0;
        }

        public String getMbText() {
            return String.format(Locale.US, "%.1f / %.1f MB", downloaded / 1000.0 / 1000.0, total / 1000.0 / 1000.0);
        }
    }

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.dataSourceRepository = new DataSourceRepository(application);
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
                allAvailableStats.setValue(stats);
                Timber.i("Stats refreshed: %d pairs found", stats.size());
                refreshDownloadedDictionaries();
            }

            @Override
            public void onError(String message) {
                Timber.w("Failed to refresh languages: %s", message);
            }
        });
    }

    public void refreshDownloadedDictionaries() {
        refreshDownloadedDictionaries(null);
    }

    public void refreshDownloadedDictionaries(List<DatabaseRemoteStorage.DictionaryStats> freshStats) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadataEntries = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        Timber.d("Found %d metadata entries", metadataEntries.size());

        File dbDir = getApplication().getDatabasePath("fake.db").getParentFile();
        List<DownloadedDictionary> list = new ArrayList<>();

        if (dbDir != null && dbDir.exists()) {
            for (String entry : metadataEntries) {
                DatabaseRemoteStorage.DictionaryStats localStats =
                        DatabaseRemoteStorage.DictionaryStats.deserialize(entry);
                if (localStats == null) {
                    Timber.w("Failed to deserialize metadata entry: %s", entry);
                    continue;
                }

                File f = new File(dbDir, localStats.fileName());
                if (f.exists()) {
                    DatabaseRemoteStorage.DictionaryStats remoteStats =
                            getStatsFor(localStats.learning(), localStats.nativeLang(), freshStats);
                    boolean updateAvailable = false;
                    boolean isLegacy = false;

                    if (remoteStats != null) {
                        if (!remoteStats.fileName().equals(localStats.fileName())) {
                            updateAvailable = true;
                        }
                    } else {
                        List<DatabaseRemoteStorage.DictionaryStats> allStats =
                                freshStats != null ? freshStats : allAvailableStats.getValue();
                        if (allStats != null && !allStats.isEmpty()) {
                            isLegacy = true;
                        }
                    }

                    list.add(new DownloadedDictionary(localStats, updateAvailable, isLegacy));
                }
            }
        }

        list.sort(Comparator.comparing((DownloadedDictionary d) -> d.learning().getDisplayName())
                .thenComparing(d -> d.nativeLang().getDisplayName()));

        if (!list.isEmpty()) {
            String curL = prefs.getString(SettingsActivity.KEY_LEARNING_LANGUAGE, "");
            String curN = prefs.getString(SettingsActivity.KEY_NATIVE_LANGUAGE, "");
            if (curL.isEmpty() || curN.isEmpty()) {
                autoSelectDictionary(list.get(0));
            }
        }

        downloadedDictionaries.postValue(list);
    }

    public void updateDictionary(DownloadedDictionary dict) {
        DatabaseRemoteStorage.DictionaryStats stats = getStatsFor(dict.learning(), dict.nativeLang());
        if (stats == null) {
            var msg =
                    "Cannot update dictionary: remote stats not found for " + dict.learning() + "-" + dict.nativeLang();
            Timber.e(msg);
            throw new RuntimeException(msg);
        }

        activeDownload.postValue(new DownloadInfo(dict.learning(), dict.nativeLang(), stats.fileName(), 0, 0));

        databaseRemoteStorage.downloadDictionary(stats, new DatabaseRemoteStorage.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                activeDownload.postValue(
                        new DownloadInfo(dict.learning(), dict.nativeLang(), stats.fileName(), downloaded, total));
            }

            @Override
            public void onSuccess(File newFile, long elapsedMs) {
                activeDownload.postValue(null);

                // 1. Delete old file
                File oldFile = dict.file(getApplication());
                if (oldFile.exists()) {
                    oldFile.delete();
                }

                // 2. Register new stats in metadata
                registerInMetadata(stats);

                // 3. Refresh and reload
                refreshDownloadedDictionaries();
                dataSourceRepository.reloadSources();
            }

            @Override
            public void onError(String errorMessage, Throwable throwable, long elapsedMs) {
                activeDownload.postValue(null);
                Timber.e("Update failed: %s", errorMessage);
                downloadError.postValue(errorMessage);
            }
        });
    }

    private void autoSelectDictionary(DownloadedDictionary dict) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        prefs.edit()
                .putString(
                        SettingsActivity.KEY_LEARNING_LANGUAGE, dict.learning().getIsoCode())
                .putString(
                        SettingsActivity.KEY_NATIVE_LANGUAGE, dict.nativeLang().getIsoCode())
                .apply();
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
        return getStatsFor(learning, nativeLang, null);
    }

    public DatabaseRemoteStorage.DictionaryStats getStatsFor(
            Language learning, Language nativeLang, List<DatabaseRemoteStorage.DictionaryStats> freshStats) {
        List<DatabaseRemoteStorage.DictionaryStats> stats =
                freshStats != null ? freshStats : allAvailableStats.getValue();
        if (stats == null || learning == null || nativeLang == null) {
            return null;
        }
        return stats.stream()
                .filter(s -> s.learning().equals(learning) && s.nativeLang().equals(nativeLang))
                .findFirst()
                .orElse(null);
    }

    public static String getDbNameFromMetadata(Context context, Language learning, Language nativeLang) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> metadataEntries = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        for (String entry : metadataEntries) {
            DatabaseRemoteStorage.DictionaryStats stats = DatabaseRemoteStorage.DictionaryStats.deserialize(entry);
            if (stats != null
                    && stats.learning().equals(learning)
                    && stats.nativeLang().equals(nativeLang)) {
                return stats.fileName();
            }
        }
        return null;
    }

    public void downloadDictionary(Language learning, Language nativeLang) {
        DatabaseRemoteStorage.DictionaryStats stats = getStatsFor(learning, nativeLang);
        if (stats == null) {
            var msg = String.format("Cannot download dictionary: stats not found for %s-%s", learning, nativeLang);
            Timber.e(msg);
            throw new RuntimeException(msg);
        }

        activeDownload.postValue(new DownloadInfo(learning, nativeLang, stats.fileName(), 0, 0));

        databaseRemoteStorage.downloadDictionary(stats, new DatabaseRemoteStorage.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                activeDownload.postValue(new DownloadInfo(learning, nativeLang, stats.fileName(), downloaded, total));
            }

            @Override
            public void onSuccess(File file, long elapsedMs) {
                activeDownload.postValue(null);
                registerInMetadata(stats);
                refreshDownloadedDictionaries();
                dataSourceRepository.reloadSources();
            }

            @Override
            public void onError(String errorMessage, Throwable throwable, long elapsedMs) {
                activeDownload.postValue(null);
                Timber.e("Download failed: %s", errorMessage);
                downloadError.postValue(errorMessage);
            }
        });
    }

    private void registerInMetadata(DatabaseRemoteStorage.DictionaryStats stats) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadata = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        metadata.removeIf(s -> {
            DatabaseRemoteStorage.DictionaryStats existing = DatabaseRemoteStorage.DictionaryStats.deserialize(s);
            return existing.learning().equals(stats.learning())
                    && existing.nativeLang().equals(stats.nativeLang());
        });
        metadata.add(stats.serialize());
        prefs.edit().putStringSet(KEY_METADATA, metadata).apply();
    }

    public void deleteDictionary(DownloadedDictionary dict) {
        if (dict.file(getApplication()).delete()) {
            unregisterFromMetadata(dict.learning(), dict.nativeLang());
            clearPrefsIfMatches(dict.learning(), dict.nativeLang());
            refreshDownloadedDictionaries();
            dataSourceRepository.reloadSources();
        }
    }

    private void unregisterFromMetadata(Language l, Language n) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        Set<String> metadata = new HashSet<>(prefs.getStringSet(KEY_METADATA, new HashSet<>()));
        metadata.removeIf(s -> {
            DatabaseRemoteStorage.DictionaryStats existing = DatabaseRemoteStorage.DictionaryStats.deserialize(s);
            return existing != null
                    && existing.learning().equals(l)
                    && existing.nativeLang().equals(n);
        });
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

    public LiveData<String> getDownloadError() {
        return downloadError;
    }

    public void clearDownloadError() {
        downloadError.setValue(null);
    }

    public void setSelectedCount(int defCount, int exCount) {
        definitionSelectedCount.setValue(defCount);
        exampleSelectedCount.setValue(exCount);
    }

    public LiveData<Integer> getDefinitionSelectedCount() {
        return definitionSelectedCount;
    }

    public LiveData<Integer> getExampleSelectedCount() {
        return exampleSelectedCount;
    }

    public void markWordAsProcessed(String word) {
        wordRepository.markWordAsProcessed(word);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dataSourceRepository.close();
    }
}
