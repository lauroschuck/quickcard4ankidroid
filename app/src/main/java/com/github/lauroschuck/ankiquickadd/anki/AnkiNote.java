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
            "ankiquickadd.notes.LearningNativeTextV19",
            List.of("LearningText", "AltLearningText", "LearningLang", "NativeText", "AltNativeText", "NativeLang", "LexicalCat",
                    "NoteHeader", "PersonalNotes", "HiddenNotes", "Audio", "SourceUrl"),
            InternalHelper.COMMON_CSS,
            List.of(new CardType(
                    "Learning-Native",
                    """
                            <div class="main-content">
                                <div class="phrase learning">
                                    {{LearningText}}
                                    {{#AltLearningText}}
                                    <div class="alt-text">[{{AltLearningText}}]</div>
                                    {{/AltLearningText}}
                                </div>
                                <div class="lang-hint"><span class="lexical-cat">{{LexicalCat}}</span> ({{LearningLang}})</div>
                            </div>
                            """,
                    """
                            <div class="main-content">
                                <div class="phrase learning">
                                    {{LearningText}}
                                    {{#AltLearningText}}
                                    <div class="alt-text">[{{AltLearningText}}]</div>
                                    {{/AltLearningText}}
                                </div>
                                <div class="lang-hint"><span class="lexical-cat">{{LexicalCat}}</span> ({{LearningLang}})</div>
                            </div>
                            
                            <hr id="answer"/>
                            
                            <div class="main-content">
                                <div class="phrase native">
                                    {{NativeText}}
                                    {{#AltNativeText}}
                                    <div class="alt-text">[{{AltNativeText}}]</div>
                                    {{/AltNativeText}}
                                </div>
                                <div class="lang-hint">({{NativeLang}})</div>
                            </div>
                            
                            {{#PersonalNotes}}
                            <div class="notes-container">
                                <div class="note-label">Personal Notes</div>
                                <div class="notes personal-notes">{{PersonalNotes}}</div>
                            </div>
                            {{/PersonalNotes}}
                            
                            {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                            """
            ),
                    new CardType(
                            "Native-Learning",
                            """
                                    <div class="main-content">
                                        <div class="phrase native">
                                            {{NativeText}}
                                            {{#AltNativeText}}
                                            <div class="alt-text">[{{AltNativeText}}]</div>
                                            {{/AltNativeText}}
                                        </div>
                                        <div class="lang-hint">({{NativeLang}})</div>
                                    </div>
                                    """,
                            """
                                    <div class="main-content">
                                        <div class="phrase native">
                                            {{NativeText}}
                                            {{#AltNativeText}}
                                            <div class="alt-text">[{{AltNativeText}}]</div>
                                            {{/AltNativeText}}
                                        </div>
                                        <div class="lang-hint">({{NativeLang}})</div>
                                    </div>
                                    
                                    <hr id="answer"/>
                                    
                                    <div class="main-content">
                                        <div class="phrase learning">
                                            {{LearningText}}
                                            {{#AltLearningText}}
                                            <div class="alt-text">[{{AltLearningText}}]</div>
                                            {{/AltLearningText}}
                                        </div>
                                        <div class="lang-hint"><span class="lexical-cat">{{LexicalCat}}</span> ({{LearningLang}})</div>
                                    </div>

                                    {{#PersonalNotes}}
                                        <div class="personal-notes">
                                            {{PersonalNotes}}
                                        </div>
                                    {{/PersonalNotes}}
                                
                                    {{#Audio}}
                                        <div class="audio">{{Audio}}</div>
                                    {{/Audio}}
                                    """
                    )),
            Set.of()
    ),
    DICTIONARY_DEFINITION(
            "ankiquickadd.notes.DictionaryDefinitionV19",
            Stream.of(Stream.of("Id", "LearningWord", "LearningLang", "LexicalCat", "NativeLang"),
                    InternalHelper.getDictionaryDefinitionindexStream()
                            .flatMap(index -> Stream.of(
                                    String.format(Locale.US, "Definition%d", index),
                                    String.format(Locale.US, "Definition%d_LearningText", index),
                                    String.format(Locale.US, "Definition%d_AltLearningText", index),
                                    String.format(Locale.US, "Definition%d_NativeText", index),
                                    String.format(Locale.US, "Definition%d_AltNativeText", index))),
                    Stream.of("NoteHeader", "PersonalNotes", "HiddenNotes", "Audio", "SourceUrl")
                    )
                    .flatMap(Function.identity())
                    .collect(Collectors.toList())
            ,
            InternalHelper.COMMON_CSS,
            Stream.concat(
                    Stream.of(
                            new CardType(
                            "LearningWord-NativeDefinitions",
                            """
                                <div class="main-content">
                                    <div class="objective">{{LearningWord}}</div>
                                    <div class="lang-hint">
                                        <span class="lexical-cat">{{LexicalCat}}</span> ({{LearningLang}})
                                    </div>
                                </div>
                                
                                <div class="example-hint">
                                    <button>Hint</button>
                                    <div class="content learning">                            
                                """
                                    + InternalHelper.dictionarySectionRepetition("""
                                            {{#DefinitionINDEX_LearningText}}
                                                <div class="row">
                                                    {{DefinitionINDEX_LearningText}}
                                                    {{#DefinitionINDEX_AltLearningText}}
                                                        <div class="alt-text">{{DefinitionINDEX_AltLearningText}}</div>
                                                    {{/DefinitionINDEX_AltLearningText}}
                                                </div>
                                            {{/DefinitionINDEX_LearningText}}
                                        """) +
                                    """
                                    </div>
                                </div>
                                
                                <script>
                                    if ($(".example-hint .content *").length > 0) {
                                        // Only show button if there is any text to be shown
                                        $(".example-hint button").show().click(function() {
                                            $(this).fadeOut(400, function() {
                                                $(".example-hint .content").fadeIn();
                                            });
                                        });
                                    }
                                </script>
                                """,
                            """
                                <div class="main-content">
                                    <div class="objective">{{LearningWord}}</div>
                                    <div class="lang-hint">
                                        <span class="lexical-cat">{{LexicalCat}}</span> ({{LearningLang}})
                                    </div>
                                </div>
                                
                                <hr id="answer"/>
                                
                                <ol class="definitions">
                                """ +
                                    InternalHelper.dictionarySectionRepetition("""
                                        {{#DefinitionINDEX}}
                                            <li>
                                                <div class="definition">{{DefinitionINDEX}}</div>
                                                <div class="example">
                                                    {{#DefinitionINDEX_LearningText}}
                                                        <div class="learning">
                                                            {{DefinitionINDEX_LearningText}}
                                                            {{#DefinitionINDEX_AltLearningText}}
                                                                <div class="alt-text">{{DefinitionINDEX_AltLearningText}}</div>
                                                            {{/DefinitionINDEX_AltLearningText}}
                                                        </div>
                                                    {{/DefinitionINDEX_LearningText}}
                                                    {{#DefinitionINDEX_NativeText}}
                                                        <div class="native">
                                                            {{DefinitionINDEX_NativeText}}
                                                            {{#DefinitionINDEX_AltNativeText}}
                                                                <div class="alt-text">{{DefinitionINDEX_AltNativeText}}</div>
                                                            {{/DefinitionINDEX_AltNativeText}}
                                                        </div>
                                                    {{/DefinitionINDEX_NativeText}}
                                                </div>
                                            </li>
                                        {{/DefinitionINDEX}}
                                    """) +
                                    """
                                </ol>
                                
                                {{#PersonalNotes}}
                                    <div class="personal-notes">
                                        {{PersonalNotes}}
                                    </div>
                                {{/PersonalNotes}}
                                
                                {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                """
                    )), InternalHelper.getDictionaryDefinitionindexStream()
                            .map(index -> new CardType(
                                    "NativeDefinitionINDEX-LearningWord".replace("INDEX", String.valueOf(index)),
                                """
                                {{#DefinitionINDEX}}
                                    <div class="main-content">
                                        <div class="objective">{{DefinitionINDEX}}</div>
                                        <div class="lang-hint">
                                            <span class="lexical-cat">{{LexicalCat}}</span> ({{NativeLang}})
                                        </div>
                                    </div>
                                
                                    {{#DefinitionINDEX_NativeText}}
                                        <div class="text">
                                            {{DefinitionINDEX_NativeText}}
                                            {{#DefinitionINDEX_AltNativeText}}
                                            <div class="alt-text">[{{DefinitionINDEX_AltNativeText}}]</div>
                                            {{/DefinitionINDEX_AltNativeText}}
                                        </div>
                                    {{/DefinitionINDEX_NativeText}}
                                {{/DefinitionINDEX}}
                                """.replaceAll("INDEX", String.valueOf(index)),
                                    """
                                    {{#DefinitionINDEX}}
                                        <div class="main-content">
                                            <div class="objective">{{DefinitionINDEX}}</div>
                                            <div class="lang-hint">
                                                <span class="lexical-cat">{{LexicalCat}}</span> ({{NativeLang}})
                                            </div>
                                        </div>
                                    
                                        {{#DefinitionINDEX_NativeText}}
                                            <div class="text">
                                                {{DefinitionINDEX_NativeText}}
                                                {{#DefinitionINDEX_AltNativeText}}
                                                <div class="alt-text">{{DefinitionINDEX_AltNativeText}}</div>
                                                {{/DefinitionINDEX_AltNativeText}}
                                            </div>
                                        {{/DefinitionINDEX_NativeText}}
                                    
                                        <hr id="answer"/>
                                    
                                        <div class="main-content">
                                            <div class="objective">{{LearningWord}}</div>
                                        </div>
                                    
                                        {{#DefinitionINDEX_LearningText}}
                                            <div class="text">
                                                {{DefinitionINDEX_LearningText}}
                                                {{#DefinitionINDEX_AltLearningText}}
                                                <div class="alt-text">{{DefinitionINDEX_AltLearningText}}</div>
                                                {{/DefinitionINDEX_AltLearningText}}
                                            </div>
                                        {{/DefinitionINDEX_LearningText}}
                                                                        
                                        {{#PersonalNotes}}
                                            <div class="personal-notes">
                                                {{PersonalNotes}}
                                            </div>
                                        {{/PersonalNotes}}
                                    
                                        {{#Audio}}
                                            <div class="audio">{{Audio}}</div>
                                        {{/Audio}}                                    

                                    {{/DefinitionINDEX}}
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
                  /*
                   * Basics
                   */
                
                  .card {
                      font-family: arial;
                      font-size: 18px;
                      text-align: center;
                      color: #202122;
                      background-color: white;
                  }
                
                  .learning {
                      font-style: italic;
                  }
                
                  .lexical-cat {
                      font-style: italic;
                  }
                
                  .text {
                      font-size: 0.9em;
                      margin-top: 0.7em;
                  }
                
                  .alt-text {
                      font-size: 0.85em;
                      color: #888;
                      display: block;
                      margin-top: 0.1em;
                  }
                
                  .alt-text::before {
                    content: "["
                  }
                
                  .alt-text::after {
                    content: "]"
                  }
                
                  /*
                   * Front
                   */
                
                  .main-content {
                      margin: 0.5em 0;
                  }
                
                  .objective {
                      font-size: 1.2em;
                  }
                
                  .lang-hint {
                      font-size: 0.75em;
                      color: gray;
                      margin-top: 0.2em;
                  }
                
                  .example-hint button {
                      display: none;
                  }
                
                  .example-hint .content {
                      display: none;
                  }
                
                  /* Full dictionary entry, learning word to definitions */
                  .example-hint .content .row {
                      font-size: 0.9em;
                      text-align: left;
                      margin-top: 0.7em;
                      padding-left: 0.5em;
                      border-left: 3px solid #eee;
                  }
                
                  /*
                   * Back
                   */
                
                  .definitions {
                      text-align: left;
                      padding-left: 0em;
                      margin: 0;
                      list-style-position: inside;
                  }
                
                  .definitions li {
                      margin-bottom: 0.8em;
                      padding-left: 0.8em;
                      border-left: 3px solid #ddd;
                  }
                
                  .definitions li .definition {
                      margin-bottom: 0.5em;
                      display: inline;
                  }
                
                  .definitions li .example {
                      font-size: 0.9em;
                  }
                
                  .definitions li .example .learning {
                      margin-top: 0.5em;
                      margin-left: 2em;
                  }
                
                  .definitions li .example .native {
                      margin-top: 0.3em;
                      margin-left: 3em;
                  }
                
                  /*
                   * Others
                   */
                
                  .personal-notes {
                      padding-top: 1.5em;
                      text-align: left;
                      font-size: 0.8em;
                      background-color: #f9f9f9;
                      padding: 0.8em 1em;
                      border-radius: 0.5em;
                  }
                
                  .audio {
                      display: none;
                  }
                
                  /*
                   * Night mode
                   */
                
                  .night_mode .objective {
                      color: #eee;
                  }
                
                  .night_mode .lang-hint {
                      color: #999;
                  }
                
                  .night_mode .alt-text {
                      color: #bbb;
                  }
                  
                  .night_mode .definitions li {
                      border-left: 3px solid #444;
                  }
                
                  .night_mode .personal-notes {
                       background-color: #222;
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
