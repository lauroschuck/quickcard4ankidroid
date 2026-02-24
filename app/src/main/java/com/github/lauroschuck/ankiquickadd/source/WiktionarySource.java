package com.github.lauroschuck.ankiquickadd.source;

import android.net.Uri;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WiktionarySource implements DictionarySource {
    private static final String TAG = "WiktionarySource";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void fetch(String word, OnResultListener listener) {
        String url = "https://en.wiktionary.org/w/api.php?action=parse&prop=text&format=json&redirects=1&page=" + Uri.encode(word);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AnkiDroidQuickAdd/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError("Unexpected code " + response);
                    return;
                }
                processResponse(response.body().string(), word, listener);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError(e.getMessage());
            }
        });
    }

    private void processResponse(String json, String word, OnResultListener listener) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("error")) {
                listener.onError("Word not found.");
                return;
            }

            String rawHtml = obj.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
            Document doc = Jsoup.parse(rawHtml);
            Element root = doc.selectFirst(".mw-parser-output");
            if (root == null) root = doc.body();

            // 1. Identify section of the source language
            Element sourceLanguageHeader = findSourceLanguageHeader(root);
            if (sourceLanguageHeader == null) {
                listener.onError("No Swedish section found.");
                return;
            }

            // 2. Pre-process: remove unwanted elements
            cleanUnwantedElements(root);

            // 3. Extract only the source language content
            String sourceLanguageSection = extractLanguageSectionFrom(sourceLanguageHeader);

            // 4. Final transformations
            Document swDoc = Jsoup.parseBodyFragment(sourceLanguageSection);
            transformLinks(swDoc);
            injectCheckboxes(swDoc);

            String finalHtml = buildHtmlPage(swDoc.body().html(), word);
            listener.onSuccess(finalHtml, word);

        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            listener.onError("Error parsing response: " + e.getMessage());
        }
    }

    private Element findSourceLanguageHeader(Element root) {
        Element header = root.selectFirst(".mw-heading2:has(#Swedish), h2:has(#Swedish)");
        if (header == null) {
            Element swId = root.selectFirst("#Swedish");
            if (swId != null) header = swId.closest(".mw-heading2, h2");
        }
        return header;
    }

    private void cleanUnwantedElements(Element root) {
        root.select(".mw-editsection, .interproject-box, figure, .mw-empty-elt").remove();

        String[] ids = {"Anagrams", "Further_reading", "Quotations"};
        for (String id : ids) {
            Element h = root.selectFirst(".mw-heading:has(#" + id + "), h2:has(#" + id + "), h3:has(#" + id + "), h4:has(#" + id + ")");
            if (h != null) removeSection(h);
        }
    }

    private void removeSection(Element header) {
        Element next = header.nextElementSibling();
        while (next != null && !next.tagName().matches("h[2-6]") && !next.hasClass("mw-heading")) {
            Element toRemove = next;
            next = next.nextElementSibling();
            toRemove.remove();
        }
        header.remove();
    }

    private String extractLanguageSectionFrom(Element header) {
        StringBuilder sb = new StringBuilder(header.outerHtml());
        Element next = header.nextElementSibling();
        while (next != null && !next.hasClass("mw-heading2") && !next.tagName().equalsIgnoreCase("h2")) {
            sb.append(next.outerHtml());
            next = next.nextElementSibling();
        }
        return sb.toString();
    }

    private void transformLinks(Document doc) {
        for (Element a : doc.select("a")) {
            String href = a.attr("href");
            if (href.contains("/wiki/") && href.contains("#Swedish")) {
                int start = href.indexOf("/wiki/") + 6;
                int end = href.indexOf("#Swedish");
                if (end > start) {
                    a.attr("href", "app://fetch/" + href.substring(start, end));
                    continue;
                }
            }
            a.unwrap();
        }
    }

    private void injectCheckboxes(Document doc) {
        for (Element ex : doc.select(".h-usage-example, .ux-example")) {
            ex.prepend("<input type='checkbox' class='example-checkbox'> ");
        }
    }

    private String buildHtmlPage(String bodyContent, String word) {
        String css = """
                body { font-family: sans-serif; padding: 12px; line-height: 1.5; color: #202122; }
                h2 { border-bottom: 1px solid #a2a9b1; margin-bottom: 0.25em; padding-top: 0.5em; font-size: 1.5em; }
                h3 { font-size: 1.25em; font-weight: bold; margin-top: 1.2em; }
                h4 { font-size: 1.15em; font-weight: bold; margin-top: 1.0em; }
                h5 { font-size: 1.05em; font-weight: bold; margin-top: 0.8em; }
                ol { padding-left: 1.5em; }
                li { margin-bottom: 0.5em; }
                .h-usage-example, .ux-example { font-style: italic; display: block; margin-top: 0.5em; }
                .h-usage-example-translation, .mention-gloss { font-style: normal; color: #54595d; display: block; font-size: 0.9em; margin-left: 2em; margin-top: 0.2em; }
                a { color: #36c; text-decoration: none; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 0.85em; }
                table th, table td { border: 1px solid #a2a9b1; padding: 6px; text-align: center; }
                table th { background-color: #f8f9fa; }
                .example-checkbox { margin-right: 8px; vertical-align: middle; }""";

        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" + css + "</style></head><body data-word='" + word + "'>" + bodyContent + "</body></html>";
    }
}
