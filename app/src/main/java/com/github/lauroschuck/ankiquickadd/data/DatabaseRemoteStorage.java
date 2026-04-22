package com.github.lauroschuck.ankiquickadd.data;

import android.content.Context;
import android.os.SystemClock;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class DatabaseRemoteStorage {
    private final Context context;
    private final OkHttpClient client;
    private final Gson gson;
    private final List<String> mirrors = new ArrayList<>();

    public DatabaseRemoteStorage(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public void discoverAvailableLanguages(DiscoveryCallback callback) {
        String json = FirebaseHelper.getDictionariesConfigJson();
        if (json.isEmpty()) {
            callback.onError("No dictionary metadata available from remote config");
            return;
        }

        try {
            Metadata root = gson.fromJson(json, Metadata.class);
            mirrors.clear();
            if (root.mirrors != null) {
                mirrors.addAll(root.mirrors);
            }

            List<DictionaryStats> stats = new ArrayList<>();
            for (DictionaryEntry entry : root.dictionaries) {
                try {
                    stats.add(new DictionaryStats(
                            Language.ofIsoCode(entry.learningLang),
                            Language.ofIsoCode(entry.nativeLang),
                            entry.headwords,
                            entry.examples,
                            entry.sizeBytes,
                            entry.glosses,
                            entry.pronunciations,
                            parseIsoDate(entry.lastModified),
                            entry.file));
                } catch (RuntimeException e) {
                    Timber.w(e, "Skipping dictionary entry: %s-%s", entry.learningLang, entry.nativeLang);
                }
            }
            callback.onSuccess(stats);
        } catch (com.google.gson.JsonSyntaxException e) {
            Timber.e(e, "Failed to parse dictionaries JSON: invalid syntax");
            callback.onError("Metadata parsing error: invalid format");
        } catch (Exception e) {
            Timber.e(e, "Unexpected error parsing dictionaries metadata");
            callback.onError("Metadata parsing error");
        }
    }

    private Instant parseIsoDate(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (java.time.format.DateTimeParseException e) {
            Timber.w(e, "Failed to parse last_modified date: %s", iso);
            return null;
        }
    }

    public void downloadDictionary(DictionaryStats stats, DownloadCallback callback) {
        if (mirrors.isEmpty()) {
            callback.onError("No download mirrors available", null, 0);
            return;
        }

        // Use the first mirror
        String mirror = mirrors.get(0);
        if (!mirror.endsWith("/")) {
            mirror += "/";
        }
        String url = mirror + stats.fileName();

        Request request = new Request.Builder().url(url).build();
        Timber.d("Downloading dictionary from %s", url);

        long start = SystemClock.uptimeMillis();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Download failure", e, SystemClock.uptimeMillis() - start);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error " + response.code(), null, SystemClock.uptimeMillis() - start);
                    return;
                }

                File outFile = context.getDatabasePath(stats.fileName());
                File tempFile = new File(outFile.getAbsolutePath() + ".tmp");
                outFile.getParentFile().mkdirs();

                try (InputStream is = response.body().byteStream();
                        FileOutputStream os = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long downloaded = 0;
                    long totalBytes = response.body().contentLength();
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                        downloaded += read;
                        callback.onProgress(downloaded, totalBytes);
                    }
                    if (tempFile.renameTo(outFile)) {
                        callback.onSuccess(outFile, SystemClock.uptimeMillis() - start);
                    } else {
                        callback.onError("Rename failed", null, SystemClock.uptimeMillis() - start);
                    }
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
        });
    }

    private static class Metadata {
        List<String> mirrors;
        List<DictionaryEntry> dictionaries;
    }

    private static class DictionaryEntry {
        @SerializedName("learning_lang")
        String learningLang;

        @SerializedName("native_lang")
        String nativeLang;

        String file;

        @SerializedName("last_modified")
        String lastModified;

        @SerializedName("size_bytes")
        long sizeBytes;

        int headwords;
        int glosses;
        int examples;
        int pronunciations;
    }

    public record DictionaryStats(
            Language learning,
            Language nativeLang,
            int headwords,
            int examples,
            long sizeBytes,
            int glosses,
            int pronunciations,
            Instant lastModified,
            String fileName) {}

    public interface DiscoveryCallback {
        void onSuccess(List<DictionaryStats> stats);

        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total);

        void onSuccess(File file, long elapsedMs);

        void onError(String message, Throwable throwable, long elapsedMs);
    }
}
