package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

public final class TextNote extends AbstractAnkiNote<TextNote.Input> {
    public TextNote(CardAssets assets) {
        super(
                "ankiquickadd.TextV49",
                generateFieldNames(),
                assets.getSharedCss() + assets.getTextCss(),
                generateCardTypes(assets));
    }

    private static List<String> generateFieldNames() {
        return Stream.of(Field.values()).map(Field::fieldName).collect(Collectors.toList());
    }

    private static CardType generateLearningToNativeCard(CardAssets assets) {
        return generateCard(
                "Learning-Native",
                CardAssets.TemplateId.TEXT_LEARNING_TO_NATIVE_FRONT,
                CardAssets.TemplateId.TEXT_LEARNING_TO_NATIVE_BACK,
                assets);
    }

    private static CardType generateNativeToLearningCard(CardAssets assets) {
        return generateCard(
                "Native-Learning",
                CardAssets.TemplateId.TEXT_NATIVE_TO_LEARNING_FRONT,
                CardAssets.TemplateId.TEXT_NATIVE_TO_LEARNING_BACK,
                assets);
    }

    private static CardType generateCard(
            String name,
            CardAssets.TemplateId frontTemplateId,
            CardAssets.TemplateId backTemplateId,
            CardAssets assets) {
        return new CardType(name, assets.getTemplate(frontTemplateId), assets.getTemplate(backTemplateId));
    }

    private static List<CardType> generateCardTypes(CardAssets assets) {
        return List.of(generateLearningToNativeCard(assets), generateNativeToLearningCard(assets));
    }

    @Override
    List<Field> getCardFields() {
        return List.of(Field.values());
    }

    enum Field implements CardField<Input> {
        LEARNING_TEXT("LearningText", input -> cleanHtml(input.learningText())),
        ALT_LEARNING_TEXT("AltLearningText", input -> null),
        LEARNING_LANG("LearningLang", Input::learningLang),
        NATIVE_TEXT("NativeText", input -> cleanHtml(input.nativeText())),
        ALT_NATIVE_TEXT("AltNativeText", input -> null),
        NATIVE_LANG("NativeLang", Input::nativeLang),
        LEXICAL_CAT("LexicalCat", Input::lexicalCategory),
        LEARNING_WORD("LearningWord", Input::headword),
        DEFINITION("Definition", Input::definition),
        PERSONAL_NOTES("PersonalNotes", input -> null),
        HIDDEN_NOTES("HiddenNotes", input -> null),
        AUDIO("Audio", Input::audioUrl),
        SOURCE_URL("SourceUrl", Input::sourceUrl);

        private final String fieldName;
        private final Function<Input, String> generator;

        Field(String fieldName, Function<Input, String> generator) {
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

    public record Input(
            String headword,
            @NonNull String learningText,
            @NonNull String learningLang,
            @NonNull String nativeText,
            @NonNull String nativeLang,
            String definition,
            String lexicalCategory,
            String audioUrl,
            String sourceUrl)
            implements AbstractAnkiNote.Input {

        @Override
        public String getHeadword() {
            return headword;
        }
    }
}
