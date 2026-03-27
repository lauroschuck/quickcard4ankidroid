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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class SearchFragment extends Fragment {

    private MainViewModel viewModel;
    private EditText searchEditText;
    private TextView warningText;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        searchEditText = view.findViewById(R.id.searchEditText);
        warningText = view.findViewById(R.id.warningText);
        Button searchButton = view.findViewById(R.id.searchButton);

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
    }

    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (!query.isEmpty()) {
            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
            ((MainActivity) requireActivity()).fetchDefinition(query);
        }
    }
}
