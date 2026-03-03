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

    LEARNING_NATIVE_TEXT_V1(
            "ankiquickadd.notes.LearningNativeTextV1",
            List.of("LearningText", "LearningLang", "NativeText", "NativeLang", "LexicalCat",
                    "NoteHeader", "Notes", "HiddenNotes", "Audio", "SourceUrl"),
            """
                    .card {
                        font-family: arial;
                        font-size: 20px;
                        text-align: center;
                        color: black;
                        background-color: white;
                    }
                    
                    .text-hints {
                        font-size: 0.75em;
                        color: gray;
                    }
                    
                    .no-style b {
                        font-weight: normal;
                    }
                    
                    .notes-container {
                         padding-top: 1em;
                    }
                    
                    .note-header {
                         font-weight: bold;
                         background-color: #eee;
                         padding: 0.5em 1em;
                    }
                    
                    .night_mode .note-header {
                         background-color: #555;
                    }
                    
                    .notes {
                         font-size: 0.8em;
                         background-color: #eee;
                         padding: 0.5em 1em;
                    }
                    
                    .night_mode .notes {
                         background-color: #555;
                    }
                    
                    .audio {
                        display: none;
                    }
                    """,
            List.of(new CardType(
                    "Learning-Native",
                    """
                            {{LearningText}}
                            <div class="text-hints">({{LexicalCat}})<br/>Lang: {{LearningLang}}</div>
                            """,
                    """
                            {{LearningText}}
                            <div class="text-hints">({{LexicalCat}})<br/>Lang: {{LearningLang}}</div>
                            
                            <hr id="answer"/>
                            
                            {{NativeText}}
                            <div class="text-hints">Lang: {{NativeLang}}</div>
                            
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
                                    {{NativeText}}
                                    <div class="text-hints">({{LexicalCat}})<br/>Lang: {{NativeLang}}</div>
                                    
                                    {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                    """,
                            """
                                    {{NativeText}}
                                    <div class="text-hints">({{LexicalCat}})<br/>Lang: {{NativeLang}}</div>
                                    
                                    <hr id="answer"/>
                                    
                                    {{LearningText}}
                                    <div class="text-hints">Lang: {{LearningLang}}</div>
                                    
                                    <div class="notes-container">
                                        {{#NoteHeader}}
                                        <div class="note-header">{{NoteHeader}}</div>
                                        {{/NoteHeader}}
                                        {{#Notes}}
                                        <div class="notes">{{Notes}}</div>
                                        {{/Notes}}
                                    </div>
                                    """
                    )),
            Set.of()
    ),
    DICTIONARY_DEFINITION_V1(
            "ankiquickadd.notes.DictionatyDefinitionV1",
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
            """
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
                     background-color: #eee;
                    }
                    
                    .night_mode .gramatical-class {
                     color: #ccc;
                     background-color: #555;
                    }
                    
                    .language {
                     font-size: 0.75em;
                     color: gray;
                    }
                    
                    .night_mode .language {
                     color: light-gray;
                    }
                    
                    div.hint button {
                     margin-top: 1em;
                     heightx: 30px;
                    }
                    
                    div.hint .example {
                     display: none;
                    }
                    
                    div.example {
                     font-size: 0.75em;
                     color: blue;
                     padding-top: 0.5em;
                    }
                    
                    .night_mode div.example {
                     color: lightblue;
                    }
                    
                    hr.alternative {
                     border-top: 1px dashed lightgray;
                    }
                    
                    table.translations {
                     border-collapse: collapse;
                     margin-left: auto;
                     margin-right: auto;
                    }
                    
                    table.translations tr.meaning {
                     border-bottom: 1px solid lightgray;
                    }
                    
                    table.translations tr.meaning:last-child {
                     border-bottom: none;
                    }
                    
                    table.translations td.translation {
                     padding: 0.25em 0.25em 0.25em 0em;
                     float: left;
                    }
                    
                    table.translations td.examples {
                     border-left: 1px solid lightgray;
                    }
                    
                    table.examples {
                     font-size: 0.75em;
                     border-collapse: collapse;
                    }
                    
                    table.examples tr.foreign td {
                     padding-top: 0.5em;
                     padding-bottom: 0.5em;
                     padding-left: 0.5em;
                    }
                    
                    table.examples tr.familiar td {
                     padding-top: 0em;
                     padding-bottom: 0.5em;
                     padding-left: 0.5em;
                    }
                    
                    table.examples td.example {
                     text-align: left;
                    }
                    
                    table.examples tr.foreign td.example {
                     color: blue;
                    }
                    
                    .night_mode table.examples tr.foreign td.example {
                     color: lightblue;
                    }
                    
                    table.examples tr.familiar td.example {
                     color: gray;
                    }
                    
                    .night_mode table.examples tr.familiar td.example {
                     color: lightgray;
                    }
                    
                    .notes-container {
                         padding-top: 1em;
                    }
                    
                    .note-header {
                         font-weight: bold;
                         background-color: #eee;
                         padding: 0.5em 1em;
                    }
                    
                    .night_mode .note-header {
                         background-color: #555;
                    }
                    
                    .notes {
                         font-size: 0.8em;
                         background-color: #eee;
                         padding: 0.5em 1em;
                    }
                    
                    .night_mode .notes {
                         background-color: #555;
                    }
                    
                    .audio {
                        display: none;
                    }
                    """,
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
