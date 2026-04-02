package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

public final class TextNote extends AbstractAnkiNote<TextNote.Input> {
    public TextNote(@NonNull CardAssets assets) {
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
        LEARNING_TEXT("LearningText", (l, n, s, i) -> cleanHtml(i.learningText())),
        ALT_LEARNING_TEXT("AltLearningText", (l, n, s, i) -> null),
        LEARNING_LANG("LearningLang", (l, n, s, i) -> l.getDisplayName()),
        NATIVE_TEXT("NativeText", (l, n, s, i) -> cleanHtml(i.nativeText())),
        ALT_NATIVE_TEXT("AltNativeText", (l, n, s, i) -> null),
        NATIVE_LANG("NativeLang", (l, n, s, i) -> n.getDisplayName()),
        LEXICAL_CAT("LexicalCat", (l, n, s, i) -> i.lexicalCategory()),
        LEARNING_WORD("LearningWord", (l, n, s, i) -> i.headword()),
        DEFINITION("Definition", (l, n, s, i) -> i.definition()),
        PERSONAL_NOTES("PersonalNotes", (l, n, s, i) -> null),
        HIDDEN_NOTES("HiddenNotes", (l, n, s, i) -> null),
        AUDIO("Audio", null),
        SOURCE_URL("SourceUrl", (l, n, s, i) -> s.toString());

        private final String fieldName;
        private final FieldFunction<Input> generator;

        Field(String fieldName, FieldFunction<Input> generator) {
            this.fieldName = fieldName;
            this.generator = generator;
        }

        @Override
        public String fieldName() {
            return fieldName;
        }

        @Override
        public FieldFunction<Input> valueGenerator() {
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
            @NonNull String nativeText,
            String definition,
            String lexicalCategory)
            implements AbstractAnkiNote.Input {}
}
