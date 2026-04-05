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

    public DictionaryRepository(Context context) {
        ServiceLoader<DictionarySource> loader = ServiceLoader.load(DictionarySource.class);
        for (DictionarySource source : loader) {
            source.setContext(context);
            sources.add(source);
        }
        Timber.d("Loaded %d dictionary sources", sources.size());
        if (!sources.isEmpty()) {
            currentSource.setValue(sources.get(0));
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
        }
    }
}
