package com.github.lauroschuck.quickcard4ankidroid;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.github.lauroschuck.quickcard4ankidroid.ui.settings.SettingsActivity;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import timber.log.Timber;

public class QuickCard4AnkiDroidApplication extends Application {
    @Override
    public void onCreate() {
        FirebaseHelper.earlyInit();
        initTheme();

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

    private void initTheme() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int themeMode = prefs.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM);
        switch (themeMode) {
            case SettingsActivity.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsActivity.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
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
