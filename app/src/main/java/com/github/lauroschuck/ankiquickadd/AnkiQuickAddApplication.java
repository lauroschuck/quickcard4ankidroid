package com.github.lauroschuck.ankiquickadd;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import timber.log.Timber;

public class AnkiQuickAddApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree() {

                @Override
                protected void log(int priority, String tag, @NonNull String message, Throwable throwable) {
                    super.log(priority, tag, message, throwable);
                    CrashReportingTree.logToCrashlytics(priority, tag, message, throwable);
                }
            });
        } else {
            Timber.plant(new CrashReportingTree());
        }

        FirebaseHelper.init(this);
    }

    private static class CrashReportingTree extends Timber.Tree {

        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable throwable) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return;
            }
            logToCrashlytics(priority, tag, message, throwable);
        }

        protected static void logToCrashlytics(int priority, String tag, @NonNull String message, Throwable throwable) {

            var priorityString =
                    switch (priority) {
                        case Log.ASSERT -> "ASSERT";
                        case Log.ERROR -> "ERROR";
                        case Log.WARN -> "WARN";
                        case Log.INFO -> "INFO";
                        case Log.DEBUG -> "DEBUG";
                        case Log.VERBOSE -> "VERBOSE";
                        default -> "UNKNOWN-" + priority;
                    };

            FirebaseHelper.logExceptionBreadcrumb(priorityString + "/" + tag + ": " + message);

            if (throwable != null && (priority == Log.WARN || priority == Log.ERROR)) {
                FirebaseCrashlytics.getInstance().recordException(throwable);
            }
        }
    }
}
