package com.github.lauroschuck.ankiquickadd;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.github.lauroschuck.ankiquickadd.data.DictionaryRepository;
import com.github.lauroschuck.ankiquickadd.data.NavigationManager;
import com.github.lauroschuck.ankiquickadd.data.WordRepository;
import com.github.lauroschuck.ankiquickadd.model.Language;
import lombok.Getter;
import lombok.Setter;

public class MainViewModel extends AndroidViewModel {
    @Getter
    private final DictionaryRepository dictionaryRepository;

    @Getter
    private final WordRepository wordRepository;

    @Getter
    private final NavigationManager navigationManager;

    private final MutableLiveData<Integer> definitionSelectedCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> exampleSelectedCount = new MutableLiveData<>(0);

    @Setter
    @Getter
    private Language lastUsedLearningLanguage;

    @Setter
    @Getter
    private Language lastUsedNativeLanguage;

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.dictionaryRepository = new DictionaryRepository(application);
        this.wordRepository = new WordRepository(application);
        this.navigationManager = new NavigationManager();
    }

    public LiveData<Integer> getDefinitionSelectedCount() {
        return definitionSelectedCount;
    }

    public void setDefinitionSelectedCount(int count) {
        definitionSelectedCount.setValue(count);
    }

    public LiveData<Integer> getExampleSelectedCount() {
        return exampleSelectedCount;
    }

    public void setExampleSelectedCount(int count) {
        exampleSelectedCount.setValue(count);
    }
}
