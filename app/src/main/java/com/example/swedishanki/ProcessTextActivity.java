package com.example.swedishanki;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProcessTextActivity extends AppCompatActivity {

    private static final String TAG = "ProcessTextActivity";
    private RecyclerView recyclerView;
    private ExampleAdapter adapter;
    private TextView statusText;
    private Button createCardsButton;

    private static final Set<String> GRAMMAR_CLASSES = new HashSet<>(Arrays.asList(
            "Noun", "Verb", "Adjective", "Adverb", "Pronoun", "Preposition", 
            "Conjunction", "Interjection", "Proper noun", "Phrase", "Participle"
    ));

    private static final Set<String> EXCLUDED_SECTIONS = new HashSet<>(Arrays.asList(
            "See also", "Conjugation", "Declension", "Pronunciation", "References"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        statusText = findViewById(R.id.statusText);
        createCardsButton = findViewById(R.id.createCardsButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExampleAdapter();
        recyclerView.setAdapter(adapter);

        createCardsButton.setOnClickListener(v -> createCards());

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        String selectedWord = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
        if (selectedWord != null) {
            fetchWiktionary(selectedWord);
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Select a word to start");
        }
    }

    private void fetchWiktionary(String word) {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Fetching '" + word + "'...");
        createCardsButton.setVisibility(View.GONE);
        
        String url = "https://en.wiktionary.org/w/api.php?action=parse"
                + "&prop=wikitext&format=json"
                + "&page=" + Uri.encode(word);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "SwedishAnkiQuickAdd/1.0 (Contact: x34689@gmail.com)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                processResponse(body);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private void processResponse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("error")) {
                runOnUiThread(() -> statusText.setText("Word not found on Wiktionary."));
                return;
            }

            String markup = obj.getAsJsonObject("parse")
                    .getAsJsonObject("wikitext")
                    .get("*").getAsString();

            var swedishPattern = Pattern.compile("(==\\s*Swedish\\s*==\\s*.*?)(?=\\n==[^=]|$)", Pattern.DOTALL);
            var matcher = swedishPattern.matcher(markup);
            
            if (matcher.find()) {
                WikitextParser.Section root = WikitextParser.parse(matcher.group(1));
                List<ExampleAdapter.Item> items = new ArrayList<>();
                extractRelevantContent(root, items);

                runOnUiThread(() -> {
                    if (items.isEmpty()) {
                        statusText.setText("No examples or grammar classes found.");
                    } else {
                        statusText.setVisibility(View.GONE);
                        adapter.setItems(items);
                        createCardsButton.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                runOnUiThread(() -> statusText.setText("No Swedish section found."));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing wikitext", e);
            runOnUiThread(() -> statusText.setText("Error parsing: " + e.getMessage()));
        }
    }

    private void extractRelevantContent(WikitextParser.Node node, List<ExampleAdapter.Item> items) {
        if (node instanceof WikitextParser.Section) {
            WikitextParser.Section s = (WikitextParser.Section) node;
            
            if (EXCLUDED_SECTIONS.contains(s.title)) {
                return;
            }

            // Always add the section header (including Etymology)
            items.add(ExampleAdapter.header(s.title));
            
            // Add immediate children content
            for (WikitextParser.Node child : s.children) {
                if (child instanceof WikitextParser.TextNode) {
                    items.add(ExampleAdapter.text(((WikitextParser.TextNode) child).text));
                } else if (child instanceof WikitextParser.EntryNode) {
                    WikitextParser.EntryNode entry = (WikitextParser.EntryNode) child;
                    // We can add the entry text too for more context if desired
                    // items.add(ExampleAdapter.text(entry.content));
                    for (WikitextParser.Node entryChild : entry.children) {
                        if (entryChild instanceof WikitextParser.EntryExampleNode) {
                            WikitextParser.EntryExampleNode ex = (WikitextParser.EntryExampleNode) entryChild;
                            items.add(ExampleAdapter.example(ex.swedish, ex.english));
                        }
                    }
                } else if (child instanceof WikitextParser.FormOfNode) {
                     WikitextParser.FormOfNode f = (WikitextParser.FormOfNode) child;
                     items.add(ExampleAdapter.text("Form of: " + f.baseWord + " (" + f.type + ")"));
                } else if (child instanceof WikitextParser.Section) {
                    // Recurse into sub-sections
                    extractRelevantContent(child, items);
                }
            }
        }
    }

    private void createCards() {
        List<ExampleAdapter.Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, "No examples selected", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder("Creating cards for:\n");
        for (ExampleAdapter.Item item : selected) {
            sb.append("- ").append(item.text1).append("\n");
        }
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
    }
}
