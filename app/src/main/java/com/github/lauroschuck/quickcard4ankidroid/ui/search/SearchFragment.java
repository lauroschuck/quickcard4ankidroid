package com.github.lauroschuck.quickcard4ankidroid.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.firebase.FirebaseHelper;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainActivity;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.NonNull;

public class SearchFragment extends Fragment {

    private MainViewModel viewModel;
    private EditText searchEditText;
    private TextView warningText;
    private RecyclerView enqueueRecyclerView;
    private View enqueueHeader;
    private ImageView enqueueChevron;
    private EnqueueAdapter adapter;
    private View searchRoot;
    private View emptyStateContainer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        searchRoot = view.findViewById(R.id.searchRoot);
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer);
        searchEditText = view.findViewById(R.id.searchEditText);
        warningText = view.findViewById(R.id.warningText);
        Button searchButton = view.findViewById(R.id.searchButton);
        enqueueRecyclerView = view.findViewById(R.id.enqueueRecyclerView);
        enqueueHeader = view.findViewById(R.id.enqueueHeader);
        enqueueChevron = view.findViewById(R.id.enqueueChevron);
        Button openSettingsButton = view.findViewById(R.id.openSettingsButton);
        var searchProgressBar = view.findViewById(R.id.searchProgressBar);

        searchButton.setOnClickListener(v -> performSearch());

        viewModel.getNavigationManager().isSearching().observe(getViewLifecycleOwner(), searching -> {
            searchProgressBar.setVisibility(searching ? View.VISIBLE : View.GONE);
            searchButton.setEnabled(!searching);
            searchEditText.setEnabled(!searching);
            if (searching) {
                searchButton.setText("");
            } else {
                searchButton.setText("Search");
            }
        });

        String currentWord = viewModel.getNavigationManager().getCurrentWord().getValue();
        if (currentWord != null && !currentWord.isEmpty()) {
            searchEditText.setText(currentWord);
        }

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        openSettingsButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToSettings();
            }
        });

        viewModel.getNavigationManager().getSearchWarning().observe(getViewLifecycleOwner(), warning -> {
            if (warning != null && !warning.isEmpty()) {
                warningText.setVisibility(View.VISIBLE);
                warningText.setText(warning);
            } else {
                warningText.setVisibility(View.GONE);
            }
        });

        viewModel.getDownloadedDictionaries().observe(getViewLifecycleOwner(), downloaded -> {
            updateUiState(downloaded);
        });

        setupEnqueueSection();
    }

    private void updateUiState(List<MainViewModel.DownloadedDictionary> downloaded) {
        boolean hasDownloaded = downloaded != null && !downloaded.isEmpty();

        if (hasDownloaded) {
            searchRoot.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);
        } else {
            searchRoot.setVisibility(View.GONE);
            emptyStateContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupEnqueueSection() {
        adapter = new EnqueueAdapter();
        enqueueRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        enqueueRecyclerView.setAdapter(adapter);

        enqueueHeader.setOnClickListener(v -> {
            if (enqueueRecyclerView.getVisibility() == View.VISIBLE) {
                enqueueRecyclerView.setVisibility(View.GONE);
                enqueueChevron.setImageResource(R.drawable.ic_chevron_down);
            } else {
                enqueueRecyclerView.setVisibility(View.VISIBLE);
                enqueueChevron.setImageResource(R.drawable.ic_chevron_up);
            }
        });

        viewModel.getWordRepository().getEnqueuedWords().observe(getViewLifecycleOwner(), words -> {
            adapter.setWords(words);
            enqueueHeader.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
            if (words.isEmpty()) {
                enqueueRecyclerView.setVisibility(View.GONE);
            }
        });

        viewModel.getWordRepository().getProcessedWords().observe(getViewLifecycleOwner(), processed -> {
            adapter.setProcessedWords(processed);
        });
    }

    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (!query.isEmpty()) {
            hideKeyboard();
            ((MainActivity) requireActivity()).fetchDefinition(query);
            FirebaseHelper.logSearch(query, FirebaseHelper.SearchMethod.MANUAL);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    private class EnqueueAdapter extends RecyclerView.Adapter<EnqueueAdapter.ViewHolder> {
        private List<String> words = new ArrayList<>();
        private Set<String> processedWords;

        public void setWords(List<String> words) {
            this.words = words != null ? words : new ArrayList<>();
            notifyDataSetChanged();
        }

        public void setProcessedWords(Set<String> processedWords) {
            this.processedWords = processedWords;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_enqueued_word, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String word = words.get(position);
            holder.textView.setText(word);

            boolean isProcessed = processedWords != null
                    && processedWords.contains(word.toLowerCase().trim());
            holder.checkMark.setVisibility(isProcessed ? View.VISIBLE : View.INVISIBLE);

            holder.itemView.setOnClickListener(v -> {
                hideKeyboard();
                String targetWord = word.toLowerCase(Locale.ROOT);
                ((MainActivity) requireActivity()).fetchDefinition(targetWord);
                FirebaseHelper.logSearch(targetWord, FirebaseHelper.SearchMethod.ENQUEUED);
            });
            holder.removeButton.setOnClickListener(v -> {
                if (isProcessed) {
                    ((MainActivity) requireActivity()).removeEnqueuedWord(word);
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_remove_enqueued_title)
                            .setMessage(getString(R.string.dialog_remove_enqueued_message, word))
                            .setPositiveButton(R.string.dialog_remove_enqueued_confirm, (dialog, which) -> {
                                ((MainActivity) requireActivity()).removeEnqueuedWord(word);
                            })
                            .setNegativeButton(R.string.dialog_remove_enqueued_cancel, null)
                            .show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ImageView checkMark;
            View removeButton;

            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.wordText);
                checkMark = itemView.findViewById(R.id.checkMark);
                removeButton = itemView.findViewById(R.id.removeButton);
            }
        }
    }
}
