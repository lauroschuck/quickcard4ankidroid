package com.github.lauroschuck.ankiquickadd.anki.notes;

import android.net.Uri;
import com.github.lauroschuck.ankiquickadd.anki.AnkiException;
import com.github.lauroschuck.ankiquickadd.model.Language;
import java.util.List;
import java.util.Set;
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

    public List<String[]> generateFields(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            String audio,
            Uri sourceUrl,
            @NonNull String[] actualFieldNames,
            @NonNull List<? extends I> cards) {
        return cards.stream()
                .map(card -> generateFields(learningLanguage, nativeLanguage, audio, sourceUrl, actualFieldNames, card))
                .collect(Collectors.toList());
    }

    private String[] generateFields(
            @NonNull Language learningLanguage,
            @NonNull Language nativeLanguage,
            String audio,
            Uri sourceUrl,
            @NonNull String[] actualFieldNames,
            @NonNull I card) {
        var fields = new String[actualFieldNames.length];

        for (CardField<I> field : getCardFields()) {
            var index = getFieldIndex(actualFieldNames, field.fieldName());
            if (field.isAudio()) {
                fields[index] = audio;
            } else {
                fields[index] = field.valueGenerator().apply(learningLanguage, nativeLanguage, sourceUrl, card);
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

    protected static int getFieldIndex(@NonNull String[] actualFieldNames, @NonNull String fieldName) {
        for (int i = 0; i < actualFieldNames.length; i++) {
            if (actualFieldNames[i].equals(fieldName)) {
                return i;
            }
        }
        throw new AnkiException(
                String.format("Field name '%s' not found in list: %s", fieldName, List.of(actualFieldNames)));
    }

    record CardType(@NonNull String name, @NonNull String frontTemplate, @NonNull String backTemplate) {}

    public sealed interface CardField<I extends Input>
            permits DictionaryNote.IndexedField, DictionaryNote.NonIndexedField, TextNote.Field {
        String fieldName();

        FieldFunction<I> valueGenerator();

        default boolean isAudio() {
            return false;
        }

        @FunctionalInterface
        interface FieldFunction<I> {
            String apply(Language learningLanguage, Language nativeLanguage, Uri sourceUrl, I input);
        }
    }

    public sealed interface Input permits DictionaryNote.Input, TextNote.Input {
        String headword();
    }
}
