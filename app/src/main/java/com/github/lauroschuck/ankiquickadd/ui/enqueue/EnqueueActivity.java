package com.github.lauroschuck.ankiquickadd.ui.enqueue;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import java.util.HashSet;
import java.util.Set;

public class EnqueueActivity extends Activity {
    public static final String KEY_ENQUEUED_WORDS = "enqueued_words";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (text == null
                && Intent.ACTION_SEND.equals(getIntent().getAction())
                && "text/plain".equals(getIntent().getType())) {
            text = getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT);
        }

        if (text != null) {
            String word = text.toString().trim().toLowerCase();
            if (!word.isEmpty()) {
                enqueueWord(word);
                Toast.makeText(this, "Enqueued: " + word, Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private void enqueueWord(String word) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> words = new HashSet<>(prefs.getStringSet(KEY_ENQUEUED_WORDS, new HashSet<>()));
        words.add(word);
        prefs.edit().putStringSet(KEY_ENQUEUED_WORDS, words).apply();
        FirebaseHelper.logEnqueueWord(word);
    }
}
