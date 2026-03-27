package com.github.lauroschuck.ankiquickadd.anki.notes;

import java.util.List;

public final class TextNote extends AbstractAnkiNote {
    public TextNote(CardAssets assets) {
        super(
                "ankiquickadd.TextV45",
                generateFieldNames(),
                assets.getSharedCss() + assets.getTextCss(),
                generateCardTypes(assets));
    }

    private static List<String> generateFieldNames() {
        return List.of(
                "LearningText",
                "AltLearningText",
                "LearningLang",
                "NativeText",
                "AltNativeText",
                "NativeLang",
                "LexicalCat",
                "LearningWord",
                "Definition",
                "PersonalNotes",
                "HiddenNotes",
                "Audio",
                "SourceUrl");
    }

    private static List<CardType> generateCardTypes(CardAssets assets) {
        return List.of(generateLearningToNativeCard(assets), generateNativeToLearningCard(assets));
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
}
