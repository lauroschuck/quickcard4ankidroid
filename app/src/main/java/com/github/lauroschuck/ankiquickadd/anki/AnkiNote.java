package com.github.lauroschuck.ankiquickadd.anki;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Defines Anki note types with their configuration and templates.
 */
public enum AnkiNote {
    SAMPLE(
        "com.ichi2.apisample",
        List.of("Expression", "Reading", "Meaning", "Furigana", "Grammar", "Sentence", "SentenceFurigana", "SentenceMeaning"),
                    """
                    .card {
                        font-family: NotoSansJP;
                        font-size: 24px;
                        text-align: center;
                        color: black;
                        background-color: white;
                        word-wrap: break-word;
                    }
                    @font-face { font-family: "NotoSansJP"; src: url('_NotoSansJP-Regular.otf'); }
                    @font-face { font-family: "NotoSansJP"; src: url('_NotoSansJP-Bold.otf'); font-weight: bold; }
                    .big { font-size: 48px; }
                    .small { font-size: 18px;}
                    """,
        List.of(
            new CardType(
                "Japanese>English",
                "<div class=big>{{Expression}}</div><br>{{Grammar}}",
                    """
                            <div class=big>{{furigana:Furigana}}</div><br>{{Meaning}}
                            <br><br>
                            {{furigana:SentenceFurigana}}<br>
                            <a href="#" onclick="document.getElementById('hint').style.display='block';return false;">Sentence Translation</a>
                            <div id="hint" style="display: none">{{SentenceMeaning}}</div>
                            <br><br>
                            {{Grammar}}<br><div class=small>{{Tags}}</div>
                            """
            ),
            new CardType(
                "English>Japanese",
                "{{Meaning}}<br><br><div class=small>{{Grammar}}<br><br>({{SentenceMeaning}})</div>",
                    """
                            <div class=big>{{furigana:Furigana}}</div><br>{{Meaning}}
                            <br><br>
                            {{furigana:SentenceFurigana}}<br>
                            <a href="#" onclick="document.getElementById('hint').style.display='block';return false;">Sentence Translation</a>
                            <div id="hint" style="display: none">{{SentenceMeaning}}</div>
                            <br><br>
                            {{Grammar}}<br><div class=small>{{Tags}}</div>
                            """
            )
        ),
        Set.of("API_Sample_App")
    ),
    SOURCE_TARGET_TEXT_V1(
            "com.github.lauroschuck.ankiquickadd.notes.language.SourceTargetTextV1",
            List.of("SourceText", "SourceLang", "TargetText", "TargetLang", "LexicalCat", "NoteHeader", "Notes", "HiddenNotes", "Audio", "SourceUrl"),
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
                            """,
                    """
                            {{SourceText}}
                            <div class="text-hints"({{LexicalCat}})<br/>Lang: {{SourceLang}}</div>
                            
                            <hr id="answer"/>
                            
                            {{TargetText}}
                            <div class="text-hints">({{TargetLang}})</div>
                            
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
                            "Target-Source",
                            """
                                    {{TargetText}}
                                    <div class="text-hints">({{LexicalCat}})<br/>Lang: {{TargetLang}}</div>
                                    
                                    {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
                                    """,
                            """
                                    {{TargetText}}
                                    <div class="text-hints"({{LexicalCat}})<br/>Lang: {{TargetLang}}</div>
                                    
                                    <hr id="answer"/>
                                    
                                    {{SourceText}}
                                    <div class="text-hints">({{SourceLang}})</div>
                                    
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
    ),;

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
