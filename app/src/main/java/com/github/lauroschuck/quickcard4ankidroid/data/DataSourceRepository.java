package com.github.lauroschuck.quickcard4ankidroid.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.github.lauroschuck.quickcard4ankidroid.source.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import timber.log.Timber;

public class DataSourceRepository {
    private final List<DataSource> sources = new ArrayList<>();
    private final MutableLiveData<DataSource> currentSource = new MutableLiveData<>();
    private final Context context;

    public DataSourceRepository(Context context) {
        this.context = context;
        reloadSources();
    }

    public void reloadSources() {
        close();
        sources.clear();
        ServiceLoader<DataSource> loader = ServiceLoader.load(DataSource.class);
        for (DataSource source : loader) {
            source.setContext(context);
            sources.add(source);
        }
        Timber.d("Loaded %d dictionary sources", sources.size());

        // If current source is null or no longer exists, pick the first one
        if (currentSource.getValue() == null && !sources.isEmpty()) {
            currentSource.setValue(sources.get(0));
        } else if (sources.isEmpty()) {
            currentSource.setValue(null);
        }
    }

    public List<DataSource> getSources() {
        return sources;
    }

    public LiveData<DataSource> getObservableCurrentSource() {
        return currentSource;
    }

    public DataSource getCurrentSource() {
        return currentSource.getValue();
    }

    public void selectSource(int index) {
        if (index >= 0 && index < sources.size()) {
            currentSource.setValue(sources.get(index));
        } else {
            currentSource.setValue(null);
        }
    }

    public void close() {
        for (DataSource source : sources) {
            source.close();
        }
    }
}
