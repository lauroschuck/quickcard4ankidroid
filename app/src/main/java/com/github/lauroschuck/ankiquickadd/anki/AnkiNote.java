package com.github.lauroschuck.ankiquickadd.anki;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Defines Anki note types with their configuration and templates.
 */
public enum AnkiNote {

    LEARNING_NATIVE_TEXT(
            "ankiquickadd.notes.LearningNativeTextV2",
            List.of("LearningText", "LearningLang", "NativeText", "NativeLang", "LexicalCat",
                    "NoteHeader", "Notes", "HiddenNotes", "Audio", "SourceUrl"),
            InternalHelper.COMMON_CSS,
            List.of(new CardType(
                    "Learning-Native",
                    """
                            {{#LexicalCat}}
                            <div class="gramatical-class">{{LexicalCat}}</div>
                            {{/LexicalCat}}
                            
                            {{LearningText}}
                            <div class="language">({{LearningLang}})</div>
                            
                            {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                            """,
                    """
                            {{#LexicalCat}}
                            <div class="gramatical-class">{{LexicalCat}}</div>
                            {{/LexicalCat}}
                            
                            {{LearningText}}
                            <div class="language">({{LearningLang}})</div>
                            
                            <hr id="answer"/>
                            
                            {{NativeText}}
                            <div class="language">({{NativeLang}})</div>
                            
                            <div class="notes-container">
                                {{#NoteHeader}}
                                <div class="note-header">{{NoteHeader}}</div>
                                {{/NoteHeader}}
                                {{#Notes}}
                                <div class="notes">{{Notes}}</div>
                                {{/Notes}}
                            </div>
                            
                            {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                            """
            ),
                    new CardType(
                            "Native-Learning",
                            """
                                    {{#LexicalCat}}
                                    <div class="gramatical-class">{{LexicalCat}}</div>
                                    {{/LexicalCat}}
                                    
                                    {{NativeText}}
                                    <div class="language">({{NativeLang}})</div>
                                    """,
                            """
                                    {{#LexicalCat}}
                                    <div class="gramatical-class">{{LexicalCat}}</div>
                                    {{/LexicalCat}}
                                    
                                    {{NativeText}}
                                    <div class="language">({{NativeLang}})</div>
                                    
                                    <hr id="answer"/>
                                    
                                    {{LearningText}}
                                    <div class="language">({{LearningLang}})</div>
                                    
                                    <div class="notes-container">
                                        {{#NoteHeader}}
                                        <div class="note-header">{{NoteHeader}}</div>
                                        {{/NoteHeader}}
                                        {{#Notes}}
                                        <div class="notes">{{Notes}}</div>
                                        {{/Notes}}
                                    </div>
                                    
                                    {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                    """
                    )),
            Set.of()
    ),
    DICTIONARY_DEFINITION(
            "ankiquickadd.notes.DictionatyDefinitionV3",
            Stream.of(Stream.of("Id", "LearningWord", "LearningLang", "LexicalCat", "NativeLang"),
                    InternalHelper.getDictionaryDefinitionindexStream()
                            .flatMap(index -> Stream.of(
                                    String.format(Locale.US, "Definition%d", index),
                                    String.format(Locale.US, "Definition%d_LearningText", index),
                                    String.format(Locale.US, "Definition%d_NativeText", index))),
                    Stream.of("NoteHeader", "Notes", "HiddenNotes", "Audio", "SourceUrl")
                    )
                    .flatMap(Function.identity())
                    .collect(Collectors.toList())
            ,
            InternalHelper.COMMON_CSS,
            Stream.concat(
                    Stream.of(
                            new CardType(
                            "Learning-Natives",
                            """
                                    {{#LexicalCat}}
                                    <div class="gramatical-class">{{LexicalCat}}</div>
                                    {{/LexicalCat}}
                                    
                                    {{LearningWord}}
                                    <div class="language">({{LearningLang}})</div>
                                    
                                    <div class="hint">
                                    <button>Hint</button>
                                    <div class="front example">
                                    """
                                    + InternalHelper.dictionarySectionRepetition("{{#DefinitionINDEX_NativeText}}{{DefinitionINDEX_NativeText}}<br/>{{/DefinitionINDEX_NativeText}}") +
                                    """
                                    </div>
                                    </div>
                                   
                                    {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                   
                                    <script>
                                    $(".hint button").click(function() {
                                    		 $(this).fadeOut(400, function() {
                                            $(this).next().fadeIn();
                                        });
                                    });
                                    </script>
                                    """,
                            """
                                    {{#LexicalCat}}
                                    <div class="gramatical-class">{{LexicalCat}}</div>
                                    {{/LexicalCat}}
                                    
                                    {{LearningWord}}
                                    <div class="language">({{LearningLang}})</div>
                                    
                                    <hr id="answer"/>
    
                                    <table class="translations">
                                    """ +
                                    InternalHelper.dictionarySectionRepetition("""
                                              {{#DefinitionINDEX}}
                                              <tr class="meaning">
                                                <td class="translation">{{DefinitionINDEX}}</td>
                                                <td class="examples">
                                                   <table class="examples">
                                                     <tr class="foreign">
                                                       <td class="language">{{#DefinitionINDEX_LearningText}}({{LearningLang}}){{/DefinitionINDEX_LearningText}}</td>
                                                       <td class="example">{{DefinitionINDEX_LearningText}}</td>
                                                     </tr>
                                                     <tr class="familiar">
                                                       <td class="language">{{#DefinitionINDEX_NativeText}}({{NativeLang}}){{/DefinitionINDEX_NativeText}}</td>
                                                       <td class="example">{{DefinitionINDEX_NativeText}}</td>
                                                     </tr>
                                                   </table>
                                                 </td>
                                              </tr>
                                              {{/DefinitionINDEX}}
                                            """) +
                                    """
                                    </table>
    
                                    <div class="notes-container">
                                        {{#NoteHeader}}
                                        <div class="note-header">{{NoteHeader}}</div>
                                        {{/NoteHeader}}
                                        {{#Notes}}
                                        <div class="notes">{{Notes}}</div>
                                        {{/Notes}}
                                    </div>
                                    """
                    )), InternalHelper.getDictionaryDefinitionindexStream()
                            .map(index -> new CardType(
                                    "NativeINDEX-Learning".replace("INDEX", String.valueOf(index)),
                                    """
                                            {{#DefinitionINDEX}}
                                            
                                            {{#LexicalCat}}
                                            <div class="gramatical-class">{{LexicalCat}}</div>
                                            {{/LexicalCat}}
                                            
                                            {{DefinitionINDEX}}
                                            <div class="language">({{NativeLang}})</div>
                                            
                                            {{#DefinitionINDEX_NativeText}}
                                            <div class="hint">
                                            <button>Hint</button>
                                            <div class="front example">
                                            {{DefinitionINDEX_NativeText}}
                                            </div>
                                            </div>
                                            {{/DefinitionINDEX_NativeText}}
                                            
                                            
                                            <script>
                                            $(".hint button").click(function() {
                                                     $(this).fadeOut(400, function() {
                                                    $(this).next().fadeIn();
                                                });
                                            });
                                            </script>
                                            
                                            {{/DefinitionINDEX}}
                                            """.replaceAll("INDEX", String.valueOf(index)),
                                    """
                                            {{#LexicalCat}}
                                            <div class="gramatical-class">{{LexicalCat}}</div>
                                            {{/LexicalCat}}
                                            
                                            {{DefinitionINDEX}}
                                            <div class="language">({{NativeLang}})</div>
                                            
                                            {{#DefinitionINDEX_NativeText}}
                                            <div class="example">
                                            {{DefinitionINDEX_NativeText}}
                                            </div>
                                            {{/DefinitionINDEX_NativeText}}
                                            
                                            <hr id="answer"/>
                                            
                                            {{LearningWord}}
                                            <div class="language">({{LearningLang}})</div>
                                            
                                            {{#DefinitionINDEX_LearningText}}
                                            <div class="example">
                                            {{DefinitionINDEX_LearningText}}
                                            </div>
                                            {{/DefinitionINDEX_LearningText}}
                                            
                                            <div class="notes-container">
                                                {{#NoteHeader}}
                                                <div class="note-header">{{NoteHeader}}</div>
                                                {{/NoteHeader}}
                                                {{#Notes}}
                                                <div class="notes">{{Notes}}</div>
                                                {{/Notes}}
                                            </div>
                                            
                                            {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                            """.replaceAll("INDEX", String.valueOf(index))
                            ))
            ).collect(Collectors.toList()),
            Set.of()
            );

    private final String modelName;
    private final List<String> fieldNames;
    private final String css;
    private final List<CardType> templates;
    private final Set<String> tags;

    AnkiNote(String modelName, List<String> fieldNames, String css, List<CardType> templates, Set<String> tags) {
        this.modelName = modelName;
        this.fieldNames = fieldNames;
        this.css = css;
        this.templates = templates;
        this.tags = tags;
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
        return tags;
    }

    public String[] getCardNames() {
        return templates.stream().map(CardType::name).toArray(String[]::new);
    }

    public String[] getQuestionTemplates() {
        return templates.stream().map(CardType::questionTemplate).toArray(String[]::new);
    }

    public String[] getAnswerTemplates() {
        return templates.stream().map(CardType::answerTemplate).toArray(String[]::new);
    }

    public record CardType(
        String name,
        String questionTemplate,
        String answerTemplate
    ) {}

    static class InternalHelper {
        static final int DICTIONARY_DEFINITION_COUNT = 5;

        static final String COMMON_CSS = """
                    .card {
                        font-family: arial;
                        font-size: 20px;
                        text-align: center;
                        color: black;
                        background-color: white;
                    }
                    
                    .gramatical-class {
                        font-size: smaller;
                        padding: 0.5em;
                        margin-bottom: 1em;
                        color: #aaa;
                        background-color: #f5f5f5;
                        border-radius: 4px;
                    }
                    
                    .night_mode .gramatical-class {
                        color: #ccc;
                        background-color: #333;
                    }
                    
                    .language {
                        font-size: 0.75em;
                        color: gray;
                        margin-top: 0.2em;
                    }
                    
                    .night_mode .language {
                        color: #999;
                    }
                    
                    .notes-container {
                         padding-top: 1.5em;
                         text-align: left;
                    }
                    
                    .note-header {
                         font-weight: bold;
                         background-color: #eee;
                         padding: 0.5em 1em;
                         border-radius: 4px 4px 0 0;
                    }
                    
                    .night_mode .note-header {
                         background-color: #444;
                    }
                    
                    .notes {
                         font-size: 0.8em;
                         background-color: #f9f9f9;
                         padding: 0.8em 1em;
                         border: 1px solid #eee;
                         border-top: none;
                         border-radius: 0 0 4px 4px;
                    }
                    
                    .night_mode .notes {
                         background-color: #222;
                         border-color: #444;
                    }
                    
                    .audio {
                        display: none;
                    }
                    
                    /* Dictionary specific styles */
                    div.hint button {
                        margin-top: 1em;
                        padding: 0.5em 1em;
                        cursor: pointer;
                    }
                    
                    div.hint .example {
                        display: none;
                    }
                    
                    div.example {
                        font-size: 0.8em;
                        color: #36c;
                        padding-top: 0.5em;
                        font-style: italic;
                    }
                    
                    .night_mode div.example {
                        color: #69f;
                    }
                    
                    table.translations {
                        border-collapse: collapse;
                        margin: 1em auto;
                        width: 100%;
                    }
                    
                    table.translations tr.meaning {
                        border-bottom: 1px solid #eee;
                    }
                    
                    table.translations td.translation {
                        padding: 0.5em;
                        text-align: left;
                        font-weight: bold;
                    }
                    
                    table.translations td.examples {
                        border-left: 1px solid #eee;
                        padding-left: 0.5em;
                    }
                    
                    table.examples {
                        font-size: 0.75em;
                        border-collapse: collapse;
                        width: 100%;
                    }
                    
                    table.examples td.example {
                        text-align: left;
                        padding: 0.2em 0.5em;
                    }
                    
                    table.examples tr.foreign td.example {
                        color: #36c;
                    }
                    
                    .night_mode table.examples tr.foreign td.example {
                        color: #69f;
                    }
                    """;

        static Stream<Integer> getDictionaryDefinitionindexStream() {
            return IntStream.range(1, DICTIONARY_DEFINITION_COUNT + 1).boxed();
        }

        static String dictionarySectionRepetition(String section) {
            return getDictionaryDefinitionindexStream()
                    .map(index -> section.replaceAll("INDEX", String.valueOf(index)))
                    .collect(Collectors.joining("\n"));
        }
    }
}
