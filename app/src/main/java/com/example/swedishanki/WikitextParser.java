package com.example.swedishanki;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikitextParser {

    public interface Node {
        String toString(int indentLevel);
    }

    public static class Section implements Node {
        public final int level;
        public final String title;
        public final List<Node> children = new ArrayList<>();

        public Section(int level, String title) {
            this.level = level;
            this.title = title;
        }

        @Override
        public String toString() {
            return toString(0);
        }

        @Override
        public String toString(int indentLevel) {
            StringBuilder sb = new StringBuilder();
            String indent = "  ".repeat(indentLevel);
            sb.append(indent).append("Section: ").append(title).append(" (L").append(level).append(")\n");
            for (Node child : children) {
                sb.append(child.toString(indentLevel + 1));
            }
            return sb.toString();
        }
    }

    public static class TextNode implements Node {
        public final String text;

        public TextNode(String text) {
            this.text = text;
        }

        @Override
        public String toString(int indentLevel) {
            return "  ".repeat(indentLevel) + "Text: " + text + "\n";
        }
        
        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static class EntryNode implements Node {
        public final String content;
        public final List<Node> children = new ArrayList<>();

        public EntryNode(String content) {
            this.content = content;
        }

        @Override
        public String toString(int indentLevel) {
            StringBuilder sb = new StringBuilder();
            sb.append("  ".repeat(indentLevel)).append("Entry: ").append(content).append("\n");
            for (Node child : children) {
                sb.append(child.toString(indentLevel + 1));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static class FormOfNode implements Node {
        public final String baseWord;
        public final String type;

        public FormOfNode(String type, String baseWord) {
            this.type = type;
            this.baseWord = baseWord;
        }

        @Override
        public String toString(int indentLevel) {
            return "  ".repeat(indentLevel) + "FormOf [" + type + "]: " + baseWord + "\n";
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static class EntryExampleNode implements Node {
        public final String rawContent;
        public String swedish;
        public String english;

        public EntryExampleNode(String content) {
            this.rawContent = content;
            parseExample();
        }

        private void parseExample() {
            Pattern templatePattern = Pattern.compile("\\{\\{(?:ux|eg|quote|quote-text)\\|sv\\|([^}|]+)(?:\\|([^}]*))?\\}\\}");
            Matcher m = templatePattern.matcher(rawContent);
            
            if (m.find()) {
                swedish = m.group(1).trim();
                String rest = m.group(2);
                if (rest != null) {
                    String[] parts = rest.split("\\|");
                    for (String p : parts) {
                        p = p.trim();
                        if (p.startsWith("t=") || p.startsWith("translation=")) {
                            english = p.substring(p.indexOf("=") + 1).trim();
                            break;
                        } else if (!p.contains("=")) {
                            if (english == null) english = p;
                        }
                    }
                }
            }
            
            if (swedish == null || english == null) {
                if (rawContent.contains("—")) {
                    String[] parts = rawContent.split("—", 2);
                    if (swedish == null) swedish = parts[0].trim();
                    if (english == null) english = parts[1].trim();
                }
            }
            
            if (swedish == null) {
                swedish = rawContent;
            }

            swedish = cleanWikitext(swedish);
            english = cleanWikitext(english);
        }

        private String cleanWikitext(String text) {
            if (text == null) return null;
            return text.replaceAll("\\[\\[(?:[^|]*\\|)?([^]]+)\\]\\]", "$1")
                       .trim();
        }

        @Override
        public String toString(int indentLevel) {
            return "  ".repeat(indentLevel) + "Example [SV: " + (swedish != null ? swedish : "?") + 
                   " | EN: " + (english != null ? english : "?") + "]\n";
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static class BulletNode implements Node {
        public final String content;

        public BulletNode(String content) {
            this.content = content;
        }

        @Override
        public String toString(int indentLevel) {
            return "  ".repeat(indentLevel) + "Bullet: " + content + "\n";
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static class IndentedNode implements Node {
        public final String content;

        public IndentedNode(String content) {
            this.content = content;
        }

        @Override
        public String toString(int indentLevel) {
            return "  ".repeat(indentLevel) + "Indented: " + content + "\n";
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }

    public static Section parse(String markup) {
        Section root = new Section(0, "Document"); 
        Stack<Section> stack = new Stack<>();
        stack.push(root);

        String[] lines = markup.split("\\R");
        Pattern headerPattern = Pattern.compile("^(={2,6})\\s*(.*?)\\s*\\1$");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m = headerPattern.matcher(trimmed);
            if (m.matches()) {
                int level = m.group(1).length();
                String title = m.group(2);
                
                Section newSection = new Section(level, title);
                
                while (stack.size() > 1 && stack.peek().level >= level) {
                    stack.pop();
                }
                
                stack.peek().children.add(newSection);
                stack.push(newSection);
            } else {
                char firstChar = trimmed.charAt(0);
                String content = trimmed.substring(1).trim();
                if (firstChar == '#') {
                    if (content.startsWith(":")) {
                        String exampleRaw = content.substring(1).trim();
                        EntryNode lastEntry = findLastEntry(stack.peek());
                        if (lastEntry != null) {
                            lastEntry.children.add(new EntryExampleNode(exampleRaw));
                        } else {
                            stack.peek().children.add(new EntryExampleNode(exampleRaw));
                        }
                    } else {
                        FormOfNode formOf = tryParseFormOf(content);
                        if (formOf != null) {
                            stack.peek().children.add(formOf);
                        } else {
                            stack.peek().children.add(new EntryNode(content));
                        }
                    }
                } else if (firstChar == '*') {
                    stack.peek().children.add(new BulletNode(content));
                } else if (firstChar == ':') {
                    stack.peek().children.add(new IndentedNode(content));
                } else {
                    stack.peek().children.add(new TextNode(trimmed));
                }
            }
        }
        
        return root;
    }

    private static FormOfNode tryParseFormOf(String content) {
        // Matches {{supine of|sv|...}}, {{past participle of|sv|...}}, etc.
        Pattern p1 = Pattern.compile("\\{\\{([^|]+)\\s*of\\|sv\\|([^}|]+)\\}\\}\\}?");
        Matcher m1 = p1.matcher(content);
        if (m1.find()) {
            return new FormOfNode(m1.group(1).trim(), m1.group(2).trim());
        }

        // Matches {{infl of|sv|lemma||form}}
        Pattern p2 = Pattern.compile("\\{\\{infl of\\|sv\\|([^}|]+)\\|\\|([^}|]+)\\}\\}\\}?");
        Matcher m2 = p2.matcher(content);
        if (m2.find()) {
            return new FormOfNode(m2.group(2).trim(), m2.group(1).trim());
        }

        return null;
    }

    private static EntryNode findLastEntry(Section section) {
        for (int i = section.children.size() - 1; i >= 0; i--) {
            if (section.children.get(i) instanceof EntryNode) {
                return (EntryNode) section.children.get(i);
            }
        }
        return null;
    }
}
