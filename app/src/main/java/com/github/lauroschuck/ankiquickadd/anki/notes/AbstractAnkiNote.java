package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.List;
import java.util.Set;

public sealed abstract class AbstractAnkiNote permits DictionaryNote, TextNote {

    private final String modelName;
    private final List<String> fieldNames;
    private final String css;
    private final List<CardType> cardTypes;

    AbstractAnkiNote(String modelName, List<String> fieldNames, String css, List<CardType> cardTypes) {
        this.modelName = modelName;
        this.fieldNames = fieldNames;
        this.css = css;
        this.cardTypes = cardTypes;
    }

    public String getModelName() {
        return modelName;
    }

    public String[] getFieldNames() {
        return fieldNames.toArray(new String[0]);
    }

    public String getCss() {
        return css;
    }

    public Set<String> getTags() {
        return Set.of();
    }

    public String[] getCardNames() {
        return cardTypes.stream().map(CardType::name).toArray(String[]::new);
    }

    public String[] getQuestionTemplates() {
        return cardTypes.stream().map(CardType::frontTemplate).toArray(String[]::new);
    }

    public String[] getAnswerTemplates() {
        return cardTypes.stream().map(CardType::backTemplate).toArray(String[]::new);
    }

    record CardType(
            String name,
            String frontTemplate,
            String backTemplate
    ) {}
}
