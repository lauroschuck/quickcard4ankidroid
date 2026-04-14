package com.github.lauroschuck.ankiquickadd.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class DatabaseRemoteStorage {
    private static final String STATS_DB_NAME = "stats.db";

    private final Context context;
    private final OkHttpClient client;

    public DatabaseRemoteStorage(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
    }

    public void discoverAvailableLanguages(DiscoveryCallback callback) {
        Request request = new Request.Builder()
                .url(FirebaseHelper.getDictionaryHostingBasePath() + STATS_DB_NAME)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e(e, "Failed to download stats.db");
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Timber.w(
                            "Call to %s returned %d: %s",
                            response.request().url(),
                            response.code(),
                            response.body().string());
                    callback.onError("Server returned " + response.code());
                    return;
                }

                File tempFile = new File(context.getCacheDir(), STATS_DB_NAME);
                try (var body = response.body();
                        InputStream is = body.byteStream();
                        FileOutputStream os = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }

                processStatsDb(tempFile, callback);
            }
        });
    }

    private void processStatsDb(File dbFile, DiscoveryCallback callback) {
        List<DictionaryStats> stats = new ArrayList<>();

        try (SQLiteDatabase db =
                SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor cursor =
                    db.rawQuery("SELECT learning_lang, native_lang, headwords, examples FROM stats", null)) {
                while (cursor.moveToNext()) {
                    try {
                        stats.add(new DictionaryStats(
                                Language.ofIsoCode(cursor.getString(0)),
                                Language.ofIsoCode(cursor.getString(1)),
                                cursor.getInt(2),
                                cursor.getInt(3)));
                    } catch (Exception e) {
                        Timber.w(
                                "Unknown language code in stats.db: %s or %s",
                                cursor.getString(0), cursor.getString(1));
                    }
                }
            }
            callback.onSuccess(stats);
        } catch (Exception e) {
            Timber.e(e, "Error processing stats.db");
            callback.onError(e.getMessage());
        } finally {
            dbFile.delete();
        }
    }

    public void downloadDictionary(Language learning, Language nativeLang, DownloadCallback callback) {
        String fileName = String.format("wiktionary_kaikki_%s-%s.db", learning.getIsoCode(), nativeLang.getIsoCode());
        Request request = new Request.Builder()
                .url(FirebaseHelper.getDictionaryHostingBasePath() + fileName)
                .build();

        long start = SystemClock.uptimeMillis();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Call failure", e, SystemClock.uptimeMillis() - start);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Timber.w(
                            "Call to %s returned %d: %s",
                            response.request().url(),
                            response.code(),
                            response.body().string());
                    callback.onError("Server returned " + response.code(), null, SystemClock.uptimeMillis() - start);
                    return;
                }

                long totalBytes = response.body().contentLength();
                File outFile = context.getDatabasePath(fileName);
                File tempFile = new File(outFile.getAbsolutePath() + ".tmp");
                outFile.getParentFile().mkdirs();

                try (InputStream is = response.body().byteStream();
                        FileOutputStream os = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long downloaded = 0;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                        downloaded += read;
                        callback.onProgress(downloaded, totalBytes);
                    }
                    var elapsed = SystemClock.uptimeMillis() - start;
                    if (tempFile.renameTo(outFile)) {
                        callback.onSuccess(outFile, elapsed);
                    } else {
                        callback.onError("Failed temp file rename", null, elapsed);
                    }
                } catch (IOException e) {
                    callback.onError("Body read error", e, SystemClock.uptimeMillis() - start);
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
        });
    }

    public record DictionaryStats(Language learning, Language nativeLang, int headwords, int examples) {}

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
