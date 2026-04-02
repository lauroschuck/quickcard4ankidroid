package com.github.lauroschuck.ankiquickadd;

import android.app.Application;
import timber.log.Timber;

public class AnkiQuickAddApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            // Optional: Plant a release tree if needed (e.g., for crash reporting)
            // Timber.plant(new ReleaseTree());
        }
    }
}
