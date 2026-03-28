package com.github.lauroschuck.ankiquickadd;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.lauroschuck.ankiquickadd.anki.AnkiDroidHelper;
import com.github.lauroschuck.ankiquickadd.anki.AnkiIntegration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.io.IOException;
import lombok.NonNull;

public class DefinitionFragment extends Fragment {

    private static final String TAG = "DefinitionFragment";
    private MainViewModel viewModel;
    private WebView webView;
    private View createCardsFabContainer;
    private TextView badgeText;
    private TabLayout noteTypeTabLayout;
    private MediaPlayer mediaPlayer;
    private AnkiIntegration ankiIntegration;

    public class WebAppInterface {
        @JavascriptInterface
        @SuppressWarnings("unused")
        public void processSelectedCards(@NonNull String json) {
            requireActivity().runOnUiThread(() -> {
                Log.d(TAG, "Selected cards JSON: " + json);
                viewModel.getCurrentSource().getCardsFromSelection(json, (n, l, a, s, i) -> {
                    requireActivity().runOnUiThread(() -> {
                        if (AnkiDroidHelper.isApiAvailable(requireContext())) {
                            // These are usually initialized in MainActivity or provided via ViewModel
                            var activity = (MainActivity) requireActivity();
                            Log.d(TAG, "Selected cards: " + i);
                            switch (noteTypeTabLayout.getSelectedTabPosition()) {
                                case 0 -> ankiIntegration.addCards(l, n, a, s, activity.getDictionaryNote(), i);
                                case 1 -> ankiIntegration.addCards(l, n, a, s, activity.getTextNote(), i);
                                default -> throw new RuntimeException(
                                        "Unknown note type for index " + noteTypeTabLayout.getSelectedTabPosition());
                            }
                        }
                    });
                });
            });
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void updateSelectedCount(int count) {
            requireActivity().runOnUiThread(() -> {
                viewModel.setSelectedCount(count);
            });
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_definition, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ankiIntegration = new AnkiIntegration((Activity) requireContext());

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        webView = view.findViewById(R.id.webView);
        createCardsFabContainer = view.findViewById(R.id.createCardsFabContainer);
        FloatingActionButton createCardsFab = view.findViewById(R.id.createCardsFab);
        badgeText = view.findViewById(R.id.badgeText);
        FloatingActionButton closeButton = view.findViewById(R.id.closeButton);
        noteTypeTabLayout = view.findViewById(R.id.noteTypeTabLayout);

        configureWebView();
        setupTabs();

        closeButton.setOnClickListener(v -> ((MainActivity) requireActivity()).closeDefinition());
        createCardsFab.setOnClickListener(v -> triggerJsExtraction());

        viewModel.getCurrentWord().observe(getViewLifecycleOwner(), word -> {
            if (word != null && !word.isEmpty()) {
                // Word fetching is still initiated by MainActivity for now
            }
        });

        viewModel.getSelectedCount().observe(getViewLifecycleOwner(), count -> {
            if (count > 0) {
                createCardsFabContainer.setVisibility(View.VISIBLE);
                badgeText.setVisibility(View.VISIBLE);
                badgeText.setText(String.valueOf(count));
            } else {
                createCardsFabContainer.setVisibility(View.GONE);
                badgeText.setVisibility(View.GONE);
            }
        });
    }

    private void setupTabs() {
        noteTypeTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String mode = tab.getPosition() == 0 ? "definitions" : "examples";
                webView.evaluateJavascript("setMode('" + mode + "')", null);
                viewModel.setSelectedCount(0);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        var examplesTab = noteTypeTabLayout.getTabAt(1);
        if (examplesTab != null) {
            examplesTab.select();
        }
    }

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebViewConsole", consoleMessage.message());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                var url = request.getUrl().toString();
                if (url.startsWith("app://fetch/")) {
                    var word = Uri.decode(url.substring("app://fetch/".length()));
                    ((MainActivity) requireActivity()).fetchDefinition(word);
                    return true;
                } else if (url.startsWith("app://play/")) {
                    var audioUrl = Uri.decode(url.substring("app://play/".length()));
                    playAudio(audioUrl);
                    return true;
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    var intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCheckboxListener();
                var mode = noteTypeTabLayout.getSelectedTabPosition() == 0 ? "definitions" : "examples";
                webView.evaluateJavascript("setMode('" + mode + "')", null);
            }
        });
    }

    private void playAudio(String url) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                ((MainActivity) requireActivity()).showSnackbar("Audio playback failed", true);
                return true;
            });
        } catch (IOException e) {
            Log.e(TAG, "Audio error", e);
        }
    }

    private void injectCheckboxListener() {
        var js = "document.addEventListener('change', function(e) {"
                + "  if (e.target.classList.contains('example-checkbox') || e.target.classList.contains('sense-checkbox')) {"
                + "    var count = document.querySelectorAll('input.example-checkbox:checked, input.sense-checkbox:checked').length;"
                + "    Android.updateSelectedCount(count);"
                + "  }"
                + "});";
        webView.evaluateJavascript(js, null);
    }

    private void triggerJsExtraction() {
        webView.evaluateJavascript(viewModel.getCurrentSource().getExtractionJs(), null);
    }

    public void loadHtml(@NonNull String html) {
        if (webView != null) {
            webView.loadDataWithBaseURL("https://en.wiktionary.org/", html, "text/html", "UTF-8", null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
