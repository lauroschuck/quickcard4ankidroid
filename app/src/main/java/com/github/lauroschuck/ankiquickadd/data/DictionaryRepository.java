package com.github.lauroschuck.ankiquickadd.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import timber.log.Timber;

public class DictionaryRepository {
    private final List<DictionarySource> sources = new ArrayList<>();
    private final MutableLiveData<DictionarySource> currentSource = new MutableLiveData<>();
    private final Context context;

    public DictionaryRepository(Context context) {
        this.context = context;
        reloadSources();
    }

    public void reloadSources() {
        sources.clear();
        ServiceLoader<DictionarySource> loader = ServiceLoader.load(DictionarySource.class);
        for (DictionarySource source : loader) {
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

    public List<DictionarySource> getSources() {
        return sources;
    }

    public LiveData<DictionarySource> getObservableCurrentSource() {
        return currentSource;
    }

    public DictionarySource getCurrentSource() {
        return currentSource.getValue();
    }

    public void selectSource(int index) {
        if (index >= 0 && index < sources.size()) {
            currentSource.setValue(sources.get(index));
        } else {
            currentSource.setValue(null);
        }
    }
}
