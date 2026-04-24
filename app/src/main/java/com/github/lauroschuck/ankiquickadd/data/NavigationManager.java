package com.github.lauroschuck.ankiquickadd.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Stack;
import lombok.Getter;
import lombok.Setter;

public class NavigationManager {
    private final MutableLiveData<String> currentWord = new MutableLiveData<>("");
    private final MutableLiveData<String> currentHtml = new MutableLiveData<>("");
    private final MutableLiveData<String> searchWarning = new MutableLiveData<>(null);
    private final Stack<String> wordHistory = new Stack<>();

    @Setter
    @Getter
    private boolean rootIsSearch = true;

    public LiveData<String> getCurrentWord() {
        return currentWord;
    }

    public void setCurrentWord(String word) {
        currentWord.setValue(word);
    }

    public LiveData<String> getCurrentHtml() {
        return currentHtml;
    }

    public void setCurrentHtml(String html) {
        currentHtml.postValue(html);
    }

    public LiveData<String> getSearchWarning() {
        return searchWarning;
    }

    public void setSearchWarning(String warning) {
        searchWarning.setValue(warning);
    }

    public Stack<String> getWordHistory() {
        return wordHistory;
    }

    public void pushHistory(String word) {
        if (wordHistory.isEmpty() || !wordHistory.peek().equals(word)) {
            wordHistory.push(word);
        }
    }

    public String popHistory() {
        if (!wordHistory.isEmpty()) {
            return wordHistory.pop();
        }
        return null;
    }

    public String peekHistory() {
        if (!wordHistory.isEmpty()) {
            return wordHistory.peek();
        }
        return null;
    }
}
