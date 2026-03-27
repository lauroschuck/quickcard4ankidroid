package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.NonNull;

public final class DictionaryNote extends AbstractAnkiNote<DictionaryNote.Input> {

    public static final int DEFINITION_FIELDS = 5;

    private static List<IndexedField> INDEXED_FIELDS;

    static {
        INDEXED_FIELDS = getDefinitionIndexesStream()
                .flatMap(index -> Stream.of(
                        new IndexedField(
                                index,
                                String.format(Locale.US, "Definition%d", index),
                                card -> cleanHtml(getPotentialField(card, index - 1, Input.Definition::definition))),
                        new IndexedField(
                                index,
                                String.format(Locale.US, "Definition%d_LearningText", index),
                                card -> cleanHtml(getPotentialField(card, index - 1, Input.Definition::learningText))),
                        new IndexedField(
                                index, String.format(Locale.US, "Definition%d_AltLearningText", index), card -> null),
                        new IndexedField(
                                index,
                                String.format(Locale.US, "Definition%d_NativeText", index),
                                card -> cleanHtml(getPotentialField(card, index - 1, Input.Definition::nativeText))),
                        new IndexedField(
                                index, String.format(Locale.US, "Definition%d_AltNativeText", index), card -> null)))
                .collect(Collectors.toList());
    }

    public DictionaryNote(CardAssets assets) {
        super(
                "ankiquickadd.DictionaryDefinitionV49",
                generateFieldNames(),
                assets.getSharedCss() + assets.getDictionaryCss(),
                generateCardTypes(assets));
    }

    private static List<String> generateFieldNames() {
        var fields = Stream.of(NonIndexedField.values())
                .map(NonIndexedField::fieldName)
                .collect(Collectors.toList());
        var nativeLangIndex = fields.indexOf(NonIndexedField.NATIVE_LANG.fieldName());
        var list = new ArrayList<String>();
        list.addAll(fields.subList(0, nativeLangIndex));
        list.addAll(INDEXED_FIELDS.stream().map(IndexedField::fieldName).collect(Collectors.toList()));
        list.addAll(fields.subList(nativeLangIndex, fields.size()));
        return list;
    }

    private static List<CardType> generateCardTypes(CardAssets assets) {
        return Stream.of(
                        Stream.of(generateLearningWordToNativeDefinitionsCard(assets)),
                        getDefinitionIndexesStream()
                                .map(index -> generateIndexedNativeDefinitionToLearningWordCard(assets, index)),
                        getDefinitionIndexesStream()
                                .map(index -> generateIndexedLearningTextToNativeTextCard(assets, index)))
                .flatMap(Function.identity())
                .collect(Collectors.toList());
    }

    private static CardType generateLearningWordToNativeDefinitionsCard(CardAssets assets) {
        return new CardType(
                "LearningWord-NativeDefinitions",
                assets.getRepeatingTemplate(
                        CardAssets.TemplateId.DICTIONARY_WORD_TO_DEFINITIONS_FRONT, DEFINITION_FIELDS),
                assets.getRepeatingTemplate(
                        CardAssets.TemplateId.DICTIONARY_WORD_TO_DEFINITIONS_BACK, DEFINITION_FIELDS));
    }

    private static CardType generateIndexedNativeDefinitionToLearningWordCard(CardAssets assets, int index) {
        return new CardType(
                String.format(Locale.US, "NativeDefinition%d-LearningWord", index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_DEFINITION_TO_WORD_FRONT, index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_DEFINITION_TO_WORD_BACK, index));
    }

    private static CardType generateIndexedLearningTextToNativeTextCard(CardAssets assets, int index) {
        return new CardType(
                String.format(Locale.US, "LearningText%d-NativeText", index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_FRONT, index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_BACK, index));
    }

    @Override
    List<CardField<Input>> getCardFields() {
        return Stream.concat(Stream.of(NonIndexedField.values()), INDEXED_FIELDS.stream())
                .collect(Collectors.toList());
    }

    private static String getPotentialField(Input card, int index, Function<Input.Definition, String> getter) {
        var definitions = card.definitions();
        if (definitions.size() > index) {
            return getter.apply(definitions.get(index));
        } else {
            // No such definition index, return null (no data on the field)
            return null;
        }
    }

    private static Stream<Integer> getDefinitionIndexesStream() {
        return IntStream.range(1, DEFINITION_FIELDS + 1).boxed();
    }

    enum NonIndexedField implements CardField<Input> {
        ID("Id", input -> String.format("%s-%s-%s", input.learningLang(), input.nativeLang(), input.headword())),
        LEARNING_WORD("LearningWord", Input::headword),
        LEARNING_LANG("LearningLang", Input::learningLang),
        LEXICAL_CAT("LexicalCat", Input::lexicalCategory),
        NATIVE_LANG("NativeLang", Input::nativeLang),
        PERSONAL_NOTES("PersonalNotes", input -> null),
        HIDDEN_NOTES("HiddenNotes", input -> null),
        AUDIO("Audio", Input::audioUrl),
        SOURCE_URL("SourceUrl", Input::sourceUrl);

        private final String fieldName;
        private final Function<Input, String> generator;

        NonIndexedField(String fieldName, Function<Input, String> generator) {
            this.fieldName = fieldName;
            this.generator = generator;
        }

        @Override
        public String fieldName() {
            return fieldName;
        }

        @Override
        public Function<Input, String> valueGenerator() {
            return generator;
        }

        @Override
        public boolean isAudio() {
            return this == AUDIO;
        }
    }

    record IndexedField(int index, String fieldName, Function<Input, String> valueGenerator)
            implements CardField<Input> {
        @Override
        public boolean isAudio() {
            return false;
        }
    }

    public record Input(
            @NonNull String headword,
            @NonNull String learningLang,
            @NonNull String nativeLang,
            String lexicalCategory,
            @NonNull List<Definition> definitions,
            String audioUrl,
            String sourceUrl)
            implements AbstractAnkiNote.Input {

        public Input {
            if (definitions.isEmpty()) {
                throw new IllegalArgumentException("Empty definitions");
            }
        }

        @Override
        public String getHeadword() {
            return headword;
        }

        public record Definition(@NonNull String definition, String learningText, String nativeText) {}
    }
}
