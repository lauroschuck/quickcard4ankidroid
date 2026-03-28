package com.github.lauroschuck.ankiquickadd;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import lombok.Getter;
import lombok.Setter;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<String> currentWord = new MutableLiveData<>("");
    private final MutableLiveData<String> searchWarning = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isDefinitionsMode = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> selectedCount = new MutableLiveData<>(0);

    @Getter
    private final List<DictionarySource> sources = new ArrayList<>();

    @Setter
    @Getter
    private DictionarySource currentSource;

    @Getter
    private final Stack<String> wordHistory = new Stack<>();

    @Setter
    @Getter
    private Language lastUsedLearningLanguage;

    @Setter
    @Getter
    private Language lastUsedNativeLanguage;

    @Setter
    @Getter
    private boolean rootIsSearch = true;

    public LiveData<String> getCurrentWord() {
        return currentWord;
    }

    public void setCurrentWord(String word) {
        currentWord.setValue(word);
    }

    public LiveData<String> getSearchWarning() {
        return searchWarning;
    }

    public void setSearchWarning(String warning) {
        searchWarning.setValue(warning);
    }

    public LiveData<Boolean> getIsDefinitionsMode() {
        return isDefinitionsMode;
    }

    public void setIsDefinitionsMode(boolean isDefinitions) {
        isDefinitionsMode.setValue(isDefinitions);
    }

    public LiveData<Integer> getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(int count) {
        selectedCount.setValue(count);
    }
}
