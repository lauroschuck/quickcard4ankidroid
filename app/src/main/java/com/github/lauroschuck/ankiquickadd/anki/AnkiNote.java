package com.github.lauroschuck.ankiquickadd.anki;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Defines Anki note types with their configuration and templates.
 */
public enum AnkiNote {

    SOURCE_TARGET_TEXT_V1(
            "ankiquickadd.notes.SourceTargetTextV1",
            List.of("SourceText", "SourceLang", "TargetText", "TargetLang", "LexicalCat",
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
                    "Source-Target",
                    """
                            {{SourceText}}
                            <div class="text-hints">({{LexicalCat}})<br/>Lang: {{SourceLang}}</div>
                            
                            {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                            """,
                    """
                            {{SourceText}}
                            <div class="text-hints">({{LexicalCat}})<br/>Lang: {{SourceLang}}</div>
                            
                            <hr id="answer"/>
                            
                            {{TargetText}}
                            <div class="text-hints">Lang: {{TargetLang}}</div>
                            
                            <div class="notes-container">
                                {{#NoteHeader}}
                                <div class="note-header">{{NoteHeader}}</div>
                                {{/NoteHeader}}
                                {{#Notes}}
                                <div class="notes">{{Notes}}</div>
                                {{/Notes}}
                            </div>
                            """
            ),
                    new CardType(
                            "Target-Source",
                            """
                                    {{TargetText}}
                                    <div class="text-hints">({{LexicalCat}})<br/>Lang: {{TargetLang}}</div>
                                    """,
                            """
                                    {{TargetText}}
                                    <div class="text-hints">({{LexicalCat}})<br/>Lang: {{TargetLang}}</div>
                                    
                                    <hr id="answer"/>
                                    
                                    {{SourceText}}
                                    <div class="text-hints">Lang: {{SourceLang}}</div>
                                    
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
    DICTIONARY_DEFINITION_V1(
            "ankiquickadd.notes.DictionatyDefinitionV1",
            List.of("SourceWord", "SourceLang", "LexicalCat", "TargetLang",
                    "Definition1", "Definition1_SourceText", "Definition1_TargetText",
                    "Definition2", "Definition2_SourceText", "Definition2_TargetText",
                    "Definition3", "Definition3_SourceText", "Definition3_TargetText",
                    "Definition4", "Definition4_SourceText", "Definition4_TargetText",
                    "Definition5", "Definition5_SourceText", "Definition5_TargetText",
                    "NoteHeader", "Notes", "HiddenNotes", "Audio", "SourceUrl"),
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
                            "Source-Targets",
                            """
                                    {{#LexicalCat}}
                                    <div class="gramatical-class">{{LexicalCat}}</div>
                                    {{/LexicalCat}}
                                    
                                    {{SourceWord}}
                                    <div class="language">({{SourceLang}})</div>
                                    
                                    <div class="hint">
                                    <button>Hint</button>
                                    <div class="front example">
                                    {{#Definition1_TargetText}}{{Definition1_TargetText}}<br/>{{/Definition1_TargetText}}
                                    {{#Definition2_TargetText}}{{Definition2_TargetText}}<br/>{{/Definition2_TargetText}}
                                    {{#Definition3_TargetText}}{{Definition3_TargetText}}<br/>{{/Definition3_TargetText}}
                                    {{#Definition4_TargetText}}{{Definition4_TargetText}}<br/>{{/Definition4_TargetText}}
                                    {{#Definition5_TargetText}}{{Definition5_TargetText}}<br/>{{/Definition5_TargetText}}
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
                                    
                                    {{SourceWord}}
                                    <div class="language">({{SourceLang}})</div>
                                    
                                    <hr id="answer"/>
    
                                    <table class="translations">
                                      <tr class="meaning">
                                        <td class="translation">{{Definition1}}</td>
                                        <td class="examples">
                                           <table class="examples">
                                             <tr class="foreign">
                                               <td class="language">{{#Definition1_SourceText}}(sv){{/Definition1_SourceText}}</td>
                                               <td class="example">{{Definition1_SourceText}}</td>
                                             </tr>
                                             <tr class="familiar">
                                               <td class="language">{{#Definition1_TargetText}}(en){{/Definition1_TargetText}}</td>
                                               <td class="example">{{Definition1_TargetText}}</td>
                                             </tr>
                                           </table>
                                         </td>
                                      </tr>
                                      {{#Definition2}}
                                      <tr class="meaning">
                                        <td class="translation">{{Definition2}}</td>
                                        <td class="examples">
                                           <table class="examples">
                                             <tr class="foreign">
                                               <td class="language">{{#Definition2_SourceText}}(sv){{/Definition2_SourceText}}</td>
                                               <td class="example">{{Definition2_SourceText}}</td>
                                             </tr>
                                             <tr class="familiar">
                                               <td class="language">{{#Definition2_TargetText}}(en){{/Definition2_TargetText}}</td>
                                               <td class="example">{{Definition2_TargetText}}</td>
                                             </tr>
                                           </table>
                                         </td>
                                      </tr>
                                      {{/Definition2}}
                                      {{#Definition3}}
                                      <tr class="meaning">
                                        <td class="translation">{{Definition3}}</td>
                                        <td class="examples">
                                           <table class="examples">
                                             <tr class="foreign">
                                               <td class="language">{{#Definition3_SourceText}}(sv){{/Definition3_SourceText}}</td>
                                               <td class="example">{{Definition3_SourceText}}</td>
                                             </tr>
                                             <tr class="familiar">
                                               <td class="language">{{#Definition3_TargetText}}(en){{/Definition3_TargetText}}</td>
                                               <td class="example">{{Definition3_TargetText}}</td>
                                             </tr>
                                           </table>
                                         </td>
                                      </tr>
                                      {{/Definition3}}
                                      {{#Definition4}}
                                      <tr class="meaning">
                                        <td class="translation">{{Definition4}}</td>
                                        <td class="examples">
                                           <table class="examples">
                                             <tr class="foreign">
                                               <td class="language">{{#Definition4_SourceText}}(sv){{/Definition4_SourceText}}</td>
                                               <td class="example">{{Definition4_SourceText}}</td>
                                             </tr>
                                             <tr class="familiar">
                                               <td class="language">{{#Definition4_TargetText}}(en){{/Definition4_TargetText}}</td>
                                               <td class="example">{{Definition4_TargetText}}</td>
                                             </tr>
                                           </table>
                                         </td>
                                      </tr>
                                      {{/Definition4}}
                                      {{#Definition5}}
                                      <tr class="meaning">
                                        <td class="translation">{{Definition5}}</td>
                                        <td class="examples">
                                           <table class="examples">
                                             <tr class="foreign">
                                               <td class="language">{{#Definition5_SourceText}}(sv){{/Definition5_SourceText}}</td>
                                               <td class="example">{{Definition5_SourceText}}</td>
                                             </tr>
                                             <tr class="familiar">
                                               <td class="language">{{#Definition5_TargetText}}(en){{/Definition5_TargetText}}</td>
                                               <td class="example">{{Definition5_TargetText}}</td>
                                             </tr>
                                           </table>
                                         </td>
                                      </tr>
                                      {{/Definition5}}
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
                    )), IntStream.range(1, 6).boxed()
                            .map(index -> new CardType(
                                    "TargetINDEX-Source".replace("INDEX", String.valueOf(index)),
                                    """
                                            {{#DefinitionINDEX}}
                                            
                                            {{#LexicalCat}}
                                            <div class="gramatical-class">{{LexicalCat}}</div>
                                            {{/LexicalCat}}
                                            
                                            {{DefinitionINDEX}}
                                            <div class="language">({{TargetLang}})</div>
                                            
                                            {{#DefinitionINDEX_TargetText}}
                                            <div class="hint">
                                            <button>Hint</button>
                                            <div class="front example">
                                            {{DefinitionINDEX_TargetText}}
                                            </div>
                                            </div>
                                            {{/DefinitionINDEX_TargetText}}
                                            
                                            
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
                                            <div class="language">({{TargetLang}})</div>
                                            
                                            {{#DefinitionINDEX_TargetText}}
                                            <div class="example">
                                            {{DefinitionINDEX_TargetText}}
                                            </div>
                                            {{/DefinitionINDEX_TargetText}}
                                            
                                            <hr id="answer"/>
                                            
                                            {{SourceWord}}
                                            <div class="language">({{SourceLang}})</div>
                                            
                                            {{#DefinitionINDEX_SourceText}}
                                            <div class="example">
                                            {{DefinitionINDEX_SourceText}}
                                            </div>
                                            {{/DefinitionINDEX_SourceText}}
                                            
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
}
