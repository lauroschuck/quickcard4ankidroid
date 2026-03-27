package com.github.lauroschuck.ankiquickadd.anki.notes;

import com.github.lauroschuck.ankiquickadd.anki.AnkiException;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public abstract sealed class AbstractAnkiNote<I extends AbstractAnkiNote.Input> permits DictionaryNote, TextNote {

    @Getter
    private final String modelName;

    @Getter
    private final List<String> fieldNames;

    @Getter
    private final String css;

    private final List<CardType> cardTypes;

    AbstractAnkiNote(
            @NonNull String modelName,
            @NonNull List<String> fieldNames,
            @NonNull String css,
            @NonNull List<CardType> cardTypes) {
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("Empty fieldNames");
        }
        if (cardTypes.isEmpty()) {
            throw new IllegalArgumentException("Empty cardTypes");
        }
        this.modelName = modelName;
        this.fieldNames = fieldNames;
        this.css = css;
        this.cardTypes = cardTypes;
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

    /*
     * Converts a list of TranslationCards into a list of Anki note fields.
     *
     * @param cards The cards to convert.
     * @param actualFieldNames The actual field names from AnkiDroid (handles user reordering).
     * @param audioProcessor A function that processes audio for a card and returns the [sound:...] tag.
     * @return A list of field arrays, one for each Anki note to be created.
     *
    public abstract List<String[]> toFieldsList(
            List<TranslationCard> cards,
            String[] actualFieldNames,
            Function<TranslationCard, String> audioProcessor);*/

    public List<String[]> generateFields(
            String[] actualFieldNames, List<I> cards, BiFunction<I, String, String> audioProcessingFunction) {
        return cards.stream()
                .map(card -> generateFields(actualFieldNames, card, audioProcessingFunction))
                .collect(Collectors.toList());
    }

    public String[] generateFields(
            String[] actualFieldNames, I card, BiFunction<I, String, String> audioProcessingFunction) {
        String[] fields = new String[actualFieldNames.length];

        for (CardField<I> field : getCardFields()) {
            int index = getFieldIndex(actualFieldNames, field.fieldName());
            fields[index] = field.valueGenerator().apply(card);
            if (field.isAudio()) {
                fields[index] = audioProcessingFunction.apply(card, fields[index]);
            }
        }

        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) {
                fields[i] = "";
            }
        }

        return fields;
    }

    abstract List<? extends CardField<I>> getCardFields();

    protected static String cleanHtml(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, Safelist.none().addTags("b", "br"));
    }

    protected static int getFieldIndex(String[] actualFieldNames, String fieldName) {
        for (int i = 0; i < actualFieldNames.length; i++) {
            if (actualFieldNames[i].equals(fieldName)) {
                return i;
            }
        }
        throw new AnkiException(
                String.format("Field name '%s' not found in list: %s", fieldName, List.of(actualFieldNames)));
    }

    record CardType(String name, String frontTemplate, String backTemplate) {}

    sealed interface CardField<T> permits DictionaryNote.IndexedField, DictionaryNote.NonIndexedField, TextNote.Field {
        String fieldName();

        Function<T, String> valueGenerator();

        boolean isAudio();
    }

    public sealed interface Input permits DictionaryNote.Input, TextNote.Input {

        String getHeadword();
    }
}
