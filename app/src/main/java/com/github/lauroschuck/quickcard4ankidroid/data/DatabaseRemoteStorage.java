package com.github.lauroschuck.quickcard4ankidroid.data;

import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.Keep;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final List<MirrorInfo> mirrors = new ArrayList<>();

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
            if (root.mirrors == null
                    || root.mirrors.isEmpty()
                    || root.dictionaries == null
                    || root.dictionaries.isEmpty()) {
                callback.onError("Invalid dictionary metadata");
                return;
            }

            List<DictionaryStats> stats = new ArrayList<>();
            for (DictionaryEntry entry : root.dictionaries) {
                stats.add(new DictionaryStats(
                        Language.ofIsoCode(entry.learningLang),
                        Language.ofIsoCode(entry.nativeLang),
                        entry.headwords,
                        entry.examples,
                        entry.sizeBytes,
                        entry.glosses,
                        entry.pronunciations,
                        Instant.ofEpochSecond(entry.lastModified),
                        entry.file,
                        root.timestamp));
            }
            mirrors.clear();
            for (Map.Entry<String, String> entry : root.mirrors.entrySet()) {
                mirrors.add(new MirrorInfo(entry.getKey(), entry.getValue()));
            }

            callback.onSuccess(stats);
        } catch (JsonSyntaxException e) {
            Timber.e(e, "Failed to parse dictionaries JSON: invalid syntax");
            callback.onError("Metadata parsing error: invalid format");
        } catch (RuntimeException e) {
            Timber.e(e, "Unexpected error parsing dictionaries metadata");
            callback.onError("Metadata parsing error");
        }
    }

    public void downloadDictionary(DictionaryStats stats, DownloadCallback callback) {
        if (mirrors.isEmpty()) {
            callback.onError("No download mirrors available", null, 0);
            return;
        }

        List<MirrorInfo> shuffledMirrors = new ArrayList<>(mirrors);
        Collections.shuffle(shuffledMirrors);

        downloadFromMirror(0, shuffledMirrors, stats, callback, SystemClock.uptimeMillis());
    }

    private void downloadFromMirror(
            int mirrorIndex,
            List<MirrorInfo> shuffledMirrors,
            DictionaryStats stats,
            DownloadCallback callback,
            long overallStart) {
        long mirrorStart = SystemClock.uptimeMillis();
        if (mirrorIndex >= shuffledMirrors.size()) {
            callback.onError(
                    "Download failed. Please try again later.", null, SystemClock.uptimeMillis() - overallStart);
            return;
        }

        MirrorInfo mirror = shuffledMirrors.get(mirrorIndex);
        String baseUrl = mirror.url();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        String url = baseUrl + stats.fileName();

        Request request = new Request.Builder().url(url).build();
        Timber.d(
                "Attempting download from mirror %d/%d [%s]: %s",
                mirrorIndex + 1, shuffledMirrors.size(), mirror.id(), url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.w(e, "Mirror %d [%s] failed: %s", mirrorIndex + 1, mirror.id(), url);
                long elapsed = SystemClock.uptimeMillis() - mirrorStart;
                boolean hasMore = mirrorIndex + 1 < shuffledMirrors.size();
                logDownloadAttempt(stats, mirror.id(), 0, false, hasMore, elapsed, e.getMessage(), e);
                downloadFromMirror(mirrorIndex + 1, shuffledMirrors, stats, callback, overallStart);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                if (!response.isSuccessful()) {
                    Timber.w(
                            "Mirror %d [%s] returned error %d: %s\n%s",
                            mirrorIndex + 1,
                            mirror.id(),
                            response.code(),
                            url,
                            response.peekBody(1000).string());
                    long elapsed = SystemClock.uptimeMillis() - mirrorStart;
                    boolean hasMore = mirrorIndex + 1 < shuffledMirrors.size();
                    logDownloadAttempt(stats, mirror.id(), code, false, hasMore, elapsed, response.message(), null);
                    response.close();
                    downloadFromMirror(mirrorIndex + 1, shuffledMirrors, stats, callback, overallStart);
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
                    long elapsed = SystemClock.uptimeMillis() - mirrorStart;
                    if (tempFile.renameTo(outFile)) {
                        logDownloadAttempt(stats, mirror.id(), code, true, false, elapsed, null, null);
                        callback.onSuccess(outFile, SystemClock.uptimeMillis() - overallStart);
                    } else {
                        boolean hasMore = mirrorIndex + 1 < shuffledMirrors.size();
                        logDownloadAttempt(stats, mirror.id(), code, false, hasMore, elapsed, "Rename failed", null);
                        callback.onError("Rename failed", null, SystemClock.uptimeMillis() - overallStart);
                    }
                } catch (IOException e) {
                    Timber.w(e, "Write failure from mirror %d [%s]: %s", mirrorIndex + 1, mirror.id(), url);
                    long elapsed = SystemClock.uptimeMillis() - mirrorStart;
                    boolean hasMore = mirrorIndex + 1 < shuffledMirrors.size();
                    logDownloadAttempt(stats, mirror.id(), code, false, hasMore, elapsed, e.getMessage(), e);
                    downloadFromMirror(mirrorIndex + 1, shuffledMirrors, stats, callback, overallStart);
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    response.close();
                }
            }
        });
    }

    private void logDownloadAttempt(
            DictionaryStats stats,
            String mirrorId,
            int code,
            boolean success,
            boolean hasMore,
            long elapsed,
            String errorMessage,
            Throwable throwable) {
        FirebaseHelper.DownloadOutcome outcome = success
                ? FirebaseHelper.DownloadOutcome.SUCCESS
                : (hasMore ? FirebaseHelper.DownloadOutcome.NA : FirebaseHelper.DownloadOutcome.FAILURE);

        FirebaseHelper.logDownloadDictionary(
                stats.learning(),
                stats.nativeLang(),
                mirrorId,
                FirebaseHelper.getMetadataVersion(),
                stats.metadataTimestamp(),
                code,
                outcome,
                elapsed,
                errorMessage,
                throwable);
    }

    @Keep
    private static class Metadata {
        Map<String, String> mirrors;
        List<DictionaryEntry> dictionaries;
        long timestamp;
    }

    @Keep
    private static class DictionaryEntry {
        @SerializedName("learning_lang")
        String learningLang;

        @SerializedName("native_lang")
        String nativeLang;

        String file;

        @SerializedName("last_modified")
        long lastModified;

        @SerializedName("size_bytes")
        long sizeBytes;

        int headwords;
        int glosses;
        int examples;
        int pronunciations;
    }

    @Keep
    public record DictionaryStats(
            Language learning,
            Language nativeLang,
            int headwords,
            int examples,
            long sizeBytes,
            int glosses,
            int pronunciations,
            Instant lastModified,
            String fileName,
            long metadataTimestamp) {

        public String serialize() {
            return String.join(
                    ":",
                    learning.getIsoCode(),
                    nativeLang.getIsoCode(),
                    String.valueOf(headwords),
                    String.valueOf(pronunciations),
                    String.valueOf(glosses),
                    String.valueOf(examples),
                    fileName,
                    String.valueOf(lastModified.getEpochSecond()),
                    String.valueOf(sizeBytes),
                    String.valueOf(metadataTimestamp));
        }

        public static DictionaryStats deserialize(String data) {
            String[] p = data.split(":");
            if (p.length < 9) {
                throw new IllegalArgumentException("Illegal data for deserialization: " + data);
            }
            return new DictionaryStats(
                    Language.ofIsoCode(p[0]),
                    Language.ofIsoCode(p[1]),
                    Integer.parseInt(p[2]),
                    Integer.parseInt(p[5]),
                    Long.parseLong(p[8]),
                    Integer.parseInt(p[4]),
                    Integer.parseInt(p[3]),
                    Instant.ofEpochSecond(Long.parseLong(p[7])),
                    p[6],
                    p.length > 9 ? Long.parseLong(p[9]) : 0);
        }
    }

    public interface DiscoveryCallback {
        void onSuccess(List<DictionaryStats> stats);

        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total);

        void onSuccess(File file, long elapsedMs);

        void onError(String message, Throwable throwable, long elapsedMs);
    }

    private record MirrorInfo(String id, String url) {}
}
