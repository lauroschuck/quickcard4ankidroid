package com.github.lauroschuck.ankiquickadd;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.lauroschuck.ankiquickadd.model.Language;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<String> currentWord = new MutableLiveData<>("");
    private final MutableLiveData<String> searchWarning = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isDefinitionsMode = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> selectedCount = new MutableLiveData<>(0);
    
    private final List<DictionarySource> sources = new ArrayList<>();
    private DictionarySource currentSource;
    private final Stack<String> wordHistory = new Stack<>();
    
    private Language lastUsedLearningLanguage;
    private Language lastUsedNativeLanguage;
    private boolean rootIsSearch = true;

    public LiveData<String> getCurrentWord() { return currentWord; }
    public void setCurrentWord(String word) { currentWord.setValue(word); }

    public LiveData<String> getSearchWarning() { return searchWarning; }
    public void setSearchWarning(String warning) { searchWarning.setValue(warning); }

    public LiveData<Boolean> getIsDefinitionsMode() { return isDefinitionsMode; }
    public void setIsDefinitionsMode(boolean isDefinitions) { isDefinitionsMode.setValue(isDefinitions); }

    public LiveData<Integer> getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int count) { selectedCount.setValue(count); }

    public List<DictionarySource> getSources() { return sources; }
    
    public DictionarySource getCurrentSource() { return currentSource; }
    public void setCurrentSource(DictionarySource source) { this.currentSource = source; }

    public Stack<String> getWordHistory() { return wordHistory; }

    public Language getLastUsedLearningLanguage() { return lastUsedLearningLanguage; }
    public void setLastUsedLearningLanguage(Language lang) { this.lastUsedLearningLanguage = lang; }

    public Language getLastUsedNativeLanguage() { return lastUsedNativeLanguage; }
    public void setLastUsedNativeLanguage(Language lang) { this.lastUsedNativeLanguage = lang; }

    public boolean isRootIsSearch() { return rootIsSearch; }
    public void setRootIsSearch(boolean rootIsSearch) { this.rootIsSearch = rootIsSearch; }
}
