package com.github.lauroschuck.ankiquickadd.anki.notes;

import android.content.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.lauroschuck.ankiquickadd.R;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.io.ByteStreamsKt;

public class CardAssets {

    private final Handlebars handlebars;
    private final Map<TemplateId, String> templateCache;
    private final String sharedCss;
    private final String dictionaryCss;
    private final String textCss;

    public CardAssets(Context context) {
        handlebars = createHandlebars();
        templateCache = Stream.of(TemplateId.values())
                .collect(Collectors.toMap(Function.identity(), t -> load(t.rawResource, context)));
        sharedCss = load(R.raw.shared_styling, context);
        dictionaryCss = load(R.raw.dictionary_styling, context);
        textCss = load(R.raw.text_styling, context);
    }

    private Handlebars createHandlebars() {
        var hb = new Handlebars().with(EscapingStrategy.NOOP); // Don't HTML-escape Anki tags

        // Use [[ ]] for Java-side generation to avoid clashing with Anki's {{ }}
        hb.setStartDelimiter("[[");
        hb.setEndDelimiter("]]");

        // Helper to handle the "INDEX" repetitions (1 to count)
        hb.registerHelper("repeat", (context, options) -> {
            int count = Integer.parseInt(context.toString());
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= count; i++) {
                sb.append(options.fn(Map.of("INDEX", i)));
            }
            return sb.toString();
        });
        return hb;
    }

    private String load(int rawResource, Context context) {
        try (var resource = context.getResources().openRawResource(rawResource)) {
            return new String(ByteStreamsKt.readBytes(resource));
        } catch (IOException e) {
            throw new RuntimeException("Error loading asset: " + e.getMessage(), e);
        }
    }

    public String getTemplate(TemplateId templateId) {
        return templateCache.get(templateId);
    }

    public String getRepeatingTemplate(TemplateId templateId, Integer repeatSize) {
        try {
            var templateString = getTemplate(templateId);
            com.github.jknack.handlebars.Template template = handlebars.compileInline(templateString);
            return template.apply(Map.of("REPEAT_SIZE", repeatSize));
        } catch (IOException e) {
            throw new RuntimeException("Template error: " + e.getMessage(), e);
        }
    }

    public String getIndexedTemplate(TemplateId templateId, Integer index) {
        try {
            var templateString = getTemplate(templateId);
            com.github.jknack.handlebars.Template template = handlebars.compileInline(templateString);
            return template.apply(Map.of("INDEX", index));
        } catch (IOException e) {
            throw new RuntimeException("Template error: " + e.getMessage(), e);
        }
    }

    public String getSharedCss() {
        return sharedCss;
    }

    public String getDictionaryCss() {
        return dictionaryCss;
    }

    public String getTextCss() {
        return textCss;
    }

    public enum TemplateId {
        DICTIONARY_WORD_TO_DEFINITIONS_FRONT(R.raw.dictionary_word_to_definitions_front),
        DICTIONARY_WORD_TO_DEFINITIONS_BACK(R.raw.dictionary_word_to_definitions_back),
        DICTIONARY_DEFINITION_TO_WORD_FRONT(R.raw.dictionary_definition_to_word_front),
        DICTIONARY_DEFINITION_TO_WORD_BACK(R.raw.dictionary_definition_to_word_back),
        DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_FRONT(R.raw.dictionary_learning_text_to_native_text_front),
        DICTIONARY_LEARNING_TEXT_TO_NATIVE_TEXT_BACK(R.raw.dictionary_learning_text_to_native_text_back),
        TEXT_LEARNING_TO_NATIVE_FRONT(R.raw.text_learning_to_native_front),
        TEXT_LEARNING_TO_NATIVE_BACK(R.raw.text_learning_to_native_back),
        TEXT_NATIVE_TO_LEARNING_FRONT(R.raw.text_native_to_learning_front),
        TEXT_NATIVE_TO_LEARNING_BACK(R.raw.text_native_to_learning_back);

        private final int rawResource;

        TemplateId(int rawResource) {
            this.rawResource = rawResource;
        }
    }
}
