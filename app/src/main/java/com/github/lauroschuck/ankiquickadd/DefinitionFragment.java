package com.github.lauroschuck.ankiquickadd;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.lauroschuck.ankiquickadd.anki.AnkiDroidHelper;
import com.github.lauroschuck.ankiquickadd.anki.AnkiIntegration;
import com.github.lauroschuck.ankiquickadd.firebase.FirebaseHelper;
import com.github.lauroschuck.ankiquickadd.source.DictionarySource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.io.IOException;
import lombok.NonNull;
import timber.log.Timber;

public class DefinitionFragment extends Fragment {

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
                Timber.d("Selected cards JSON received from WebView");
                var currentSource = viewModel.getDictionaryRepository().getCurrentSource();
                if (currentSource == null) {
                    Timber.e("No current source selected");
                    return;
                }
                var selectedCards = currentSource.getCardsFromSelection(json);
                if (AnkiDroidHelper.isApiAvailable(requireContext())) {
                    var activity = (MainActivity) requireActivity();
                    int count = selectedCards.inputs().size();
                    Timber.i("Adding %d selected cards to Anki", count);

                    FirebaseHelper.NoteType noteType;
                    String headword = "";
                    if (!selectedCards.inputs().isEmpty()) {
                        headword = selectedCards.inputs().get(0).headword();
                    }

                    if (selectedCards instanceof DictionarySource.SelectedDictionaryCards dictCards) {
                        noteType = FirebaseHelper.NoteType.DICTIONARY;
                        ankiIntegration.addCards(
                                dictCards.learningLanguage(),
                                dictCards.nativeLanguage(),
                                dictCards.audioUrl(),
                                dictCards.sourceUrl(),
                                activity.getDictionaryNote(),
                                dictCards.inputs(),
                                activity::markWordAsProcessed);
                        FirebaseHelper.logExportDictionaryCards(headword, dictCards);
                    } else if (selectedCards instanceof DictionarySource.SelectedTextCards textCards) {
                        noteType = FirebaseHelper.NoteType.TEXT;
                        ankiIntegration.addCards(
                                textCards.learningLanguage(),
                                textCards.nativeLanguage(),
                                textCards.audioUrl(),
                                textCards.sourceUrl(),
                                activity.getTextNote(),
                                textCards.inputs(),
                                activity::markWordAsProcessed);
                        FirebaseHelper.logExportTextCards(headword, textCards);
                    } else {
                        Timber.e(
                                "Unknown selected card type: %s",
                                selectedCards.getClass().getName());
                        throw new RuntimeException("Unknown selected card type " + selectedCards);
                    }
                    FirebaseHelper.logExportCards(headword, noteType, count);
                } else {
                    Timber.w("AnkiDroid API not available");
                }
            });
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void updateSelectedCount(int count) {
            requireActivity().runOnUiThread(() -> {
                Timber.v("Update selected count: %d", count);
                if (noteTypeTabLayout.getSelectedTabPosition() == 0) {
                    viewModel.setDefinitionSelectedCount(count);
                } else {
                    viewModel.setExampleSelectedCount(count);
                }
            });
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void showToast(String message) {
            requireActivity().runOnUiThread(() -> {
                Timber.d("Toast from WebView: %s", message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
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

        viewModel.getNavigationManager().getCurrentHtml().observe(getViewLifecycleOwner(), html -> {
            if (html != null && !html.isEmpty()) {
                loadHtml(html);
            }
        });

        closeButton.setOnClickListener(v -> ((MainActivity) requireActivity()).closeDefinition());
        createCardsFab.setOnClickListener(v -> triggerJsExtraction());

        viewModel.getDefinitionSelectedCount().observe(getViewLifecycleOwner(), count -> {
            if (noteTypeTabLayout.getSelectedTabPosition() == 0) {
                updateBadge(count);
            }
        });

        viewModel.getExampleSelectedCount().observe(getViewLifecycleOwner(), count -> {
            if (noteTypeTabLayout.getSelectedTabPosition() == 1) {
                updateBadge(count);
            }
        });
    }

    private void updateBadge(int count) {
        if (count > 0) {
            createCardsFabContainer.setVisibility(View.VISIBLE);
            badgeText.setVisibility(View.VISIBLE);
            badgeText.setText(String.valueOf(count));
        } else {
            createCardsFabContainer.setVisibility(View.GONE);
            badgeText.setVisibility(View.GONE);
        }
    }

    private void setupTabs() {
        noteTypeTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String mode = tab.getPosition() == 0 ? "definitions" : "examples";
                Timber.d("Switching mode to: %s", mode);
                webView.evaluateJavascript("setMode('" + mode + "')", null);
                FirebaseHelper.logChangeNoteType(
                        tab.getPosition() == 0 ? FirebaseHelper.NoteType.DICTIONARY : FirebaseHelper.NoteType.TEXT);

                int count = tab.getPosition() == 0
                        ? (viewModel.getDefinitionSelectedCount().getValue() != null
                                ? viewModel.getDefinitionSelectedCount().getValue()
                                : 0)
                        : (viewModel.getExampleSelectedCount().getValue() != null
                                ? viewModel.getExampleSelectedCount().getValue()
                                : 0);
                updateBadge(count);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void configureWebView() {
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Timber.tag("WebViewConsole")
                        .d(
                                "[%s:%d] %s",
                                consoleMessage.sourceId(), consoleMessage.lineNumber(), consoleMessage.message());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                var url = request.getUrl().toString();
                Timber.d("WebView loading URL: %s", url);
                if (url.startsWith("app://fetch/")) {
                    var word = Uri.decode(url.substring("app://fetch/".length()));
                    ((MainActivity) requireActivity()).fetchDefinition(word);
                    return true;
                } else if (url.startsWith("app://play/")) {
                    var audioUrl = Uri.decode(url.substring("app://play/".length()));
                    playAudio(audioUrl);
                    return true;
                } else if (url.startsWith("app://source/")) {
                    viewModel.getDictionaryRepository().getCurrentSource().handleSourceAction(url, webView);
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
                Timber.v("WebView page finished: %s", url);
                injectCheckboxListener();
                var mode = noteTypeTabLayout.getSelectedTabPosition() == 0 ? "definitions" : "examples";
                webView.evaluateJavascript("setMode('" + mode + "')", null);
            }
        });
    }

    private void playAudio(String url) {
        Timber.i("Playing audio from: %s", url);
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
                Timber.e("MediaPlayer error: what=%d, extra=%d", what, extra);
                ((MainActivity) requireActivity()).showSnackbar("Audio playback failed", true);
                return true;
            });
        } catch (IOException e) {
            Timber.e(e, "Error setting data source for audio");
            ((MainActivity) requireActivity()).showSnackbar("Audio playback failed", true);
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
        if (!ankiIntegration.hasPermission()) {
            ankiIntegration.requestPermissionWithRationale(requireActivity(), AnkiIntegration.AD_PERM_REQUEST);
            return;
        }
        var currentSource = viewModel.getDictionaryRepository().getCurrentSource();
        Timber.d("Triggering JS extraction for source: %s", currentSource.getName());
        webView.evaluateJavascript(currentSource.getExtractionJs(), null);
    }

    public void loadHtml(@NonNull String html) {
        if (webView != null) {
            Timber.v("Loading HTML content into WebView (length: %d)", html.length());
            webView.loadDataWithBaseURL("https://en.wiktionary.org/", html, "text/html", "UTF-8", null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            Timber.d("Releasing MediaPlayer");
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
