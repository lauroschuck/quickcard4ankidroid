package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class DictionaryNote extends AbstractAnkiNote {

    public static final int DEFINITION_FIELDS = 5;

    public DictionaryNote(CardAssets assets) {
        super("ankiquickadd.DictionaryDefinitionV45", generateFieldNames(), assets.getSharedCss() + assets.getDictionaryCss(), generateCardTypes(assets));
    }

    static Stream<Integer> getDefinitionSizedStream() {
        return IntStream.range(1, DEFINITION_FIELDS + 1).boxed();
    }

    private static List<String> generateFieldNames() {
        return Stream.of(Stream.of("Id", "LearningWord", "LearningLang", "LexicalCat", "NativeLang"),
                        getDefinitionSizedStream()
                                .flatMap(index -> Stream.of(
                                        String.format(Locale.US, "Definition%d", index),
                                        String.format(Locale.US, "Definition%d_LearningText", index),
                                        String.format(Locale.US, "Definition%d_AltLearningText", index),
                                        String.format(Locale.US, "Definition%d_NativeText", index),
                                        String.format(Locale.US, "Definition%d_AltNativeText", index))),
                        Stream.of("PersonalNotes", "HiddenNotes", "Audio", "SourceUrl")
                )
                .flatMap(Function.identity())
                .collect(Collectors.toList());
    }

    private static List<CardType> generateCardTypes(CardAssets assets) {
        return Stream.of(
                Stream.of(generateLearningWordToNativeDefinitionsCard(assets)),
                        getDefinitionSizedStream()
                                .map(index -> generateIndexedNativeDefinitionToLearningWordCard(assets, index)),
                        getDefinitionSizedStream()
                                .map(index -> generateIndexedLearningTextToNativeTextCard(assets, index)))
                .flatMap(Function.identity())
                .toList();
    }

    private static CardType generateLearningWordToNativeDefinitionsCard(CardAssets assets) {
        return new CardType(
        "LearningWord-NativeDefinitions",
                assets.getRepeatingTemplate(CardAssets.TemplateId.DICTIONARY_WORD_TO_DEFINITIONS_FRONT, DEFINITION_FIELDS),
                assets.getRepeatingTemplate(CardAssets.TemplateId.DICTIONARY_WORD_TO_DEFINITIONS_BACK, DEFINITION_FIELDS));
    }

    private static CardType generateIndexedNativeDefinitionToLearningWordCard(CardAssets assets, int index) {
        return new CardType(
                "NativeDefinition%d-LearningWord".formatted(index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_DEFINITION_TO_WORD_FRONT, index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_DEFINITION_TO_WORD_BACK, index));
    }

    private static CardType generateIndexedLearningTextToNativeTextCard(CardAssets assets, int index) {
        return new CardType(
                "LearningText%d-NativeText".formatted(index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_FRONT, index),
                assets.getIndexedTemplate(CardAssets.TemplateId.DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_BACK, index));
    }

}
