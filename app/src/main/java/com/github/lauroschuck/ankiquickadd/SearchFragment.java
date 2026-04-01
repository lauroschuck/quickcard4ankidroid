package com.github.lauroschuck.ankiquickadd;

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
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        searchEditText = view.findViewById(R.id.searchEditText);
        warningText = view.findViewById(R.id.warningText);
        Button searchButton = view.findViewById(R.id.searchButton);
        enqueueRecyclerView = view.findViewById(R.id.enqueueRecyclerView);
        enqueueHeader = view.findViewById(R.id.enqueueHeader);
        enqueueChevron = view.findViewById(R.id.enqueueChevron);

        searchButton.setOnClickListener(v -> performSearch());
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        viewModel.getSearchWarning().observe(getViewLifecycleOwner(), warning -> {
            if (warning != null && !warning.isEmpty()) {
                warningText.setVisibility(View.VISIBLE);
                warningText.setText(warning);
            } else {
                warningText.setVisibility(View.GONE);
            }
        });

        setupEnqueueSection();
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

        viewModel.getEnqueuedWords().observe(getViewLifecycleOwner(), words -> {
            adapter.setWords(words);
            enqueueHeader.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
            if (words.isEmpty()) {
                enqueueRecyclerView.setVisibility(View.GONE);
            }
        });

        viewModel.getProcessedWords().observe(getViewLifecycleOwner(), processed -> {
            adapter.setProcessedWords(processed);
        });
    }

    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (!query.isEmpty()) {
            hideKeyboard();
            ((MainActivity) requireActivity()).fetchDefinition(query);
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
                ((MainActivity) requireActivity()).fetchDefinition(word);
            });
            holder.removeButton.setOnClickListener(v -> {
                if (isProcessed) {
                    ((MainActivity) requireActivity()).removeEnqueuedWord(word);
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Remove Enqueued Word")
                            .setMessage("Are you sure you want to remove '" + word
                                    + "'?\nIt may not have been added to Anki yet.")
                            .setPositiveButton("Remove", (dialog, which) -> {
                                ((MainActivity) requireActivity()).removeEnqueuedWord(word);
                            })
                            .setNegativeButton("Cancel", null)
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
