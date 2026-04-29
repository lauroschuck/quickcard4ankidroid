package com.github.lauroschuck.quickcard4ankidroid.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.github.lauroschuck.quickcard4ankidroid.R;
import com.github.lauroschuck.quickcard4ankidroid.model.Language;
import com.github.lauroschuck.quickcard4ankidroid.ui.main.MainViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryAdapter.ViewHolder> {

    private List<MainViewModel.DownloadedDictionary> dictionaries = new ArrayList<>();
    private String activeLearningIso;
    private String activeNativeIso;
    private MainViewModel.DownloadInfo activeDownload;
    private final OnDictionaryActionListener listener;

    public interface OnDictionaryActionListener {
        void onSelect(Language learning, Language nativeLang);

        void onDelete(MainViewModel.DownloadedDictionary dict);

        void onUpdate(MainViewModel.DownloadedDictionary dict);
    }

    public DictionaryAdapter(OnDictionaryActionListener listener) {
        this.listener = listener;
    }

    public void setData(
            List<MainViewModel.DownloadedDictionary> dictionaries,
            String learningIso,
            String nativeIso,
            MainViewModel.DownloadInfo activeDownload) {
        this.dictionaries = dictionaries;
        this.activeLearningIso = learningIso;
        this.activeNativeIso = nativeIso;
        this.activeDownload = activeDownload;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dictionary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (activeDownload != null && position == dictionaries.size()) {
            bindDownloading(holder, activeDownload);
            return;
        }

        MainViewModel.DownloadedDictionary dict = dictionaries.get(position);
        bindDownloaded(holder, dict);
    }

    private void bindDownloaded(ViewHolder holder, MainViewModel.DownloadedDictionary dict) {
        holder.progressContainer.setVisibility(View.GONE);
        holder.deleteButton.setVisibility(View.VISIBLE);

        holder.updateButton.setVisibility(dict.updateAvailable() ? View.VISIBLE : View.GONE);
        holder.legacyIcon.setVisibility(dict.isLegacy() ? View.VISIBLE : View.GONE);

        Language learning = dict.learning();
        Language nativeLang = dict.nativeLang();
        holder.nameText.setText(
                String.format(Locale.US, "%s-%s", learning.getDisplayName(), nativeLang.getDisplayName()));

        boolean isActive = learning.getIsoCode().equals(activeLearningIso)
                && nativeLang.getIsoCode().equals(activeNativeIso);
        holder.radioButton.setVisibility(View.VISIBLE);
        holder.radioButton.setChecked(isActive);

        holder.itemView.setOnClickListener(v -> listener.onSelect(learning, nativeLang));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(dict));
        holder.updateButton.setOnClickListener(v -> listener.onUpdate(dict));
    }

    private void bindDownloading(ViewHolder holder, MainViewModel.DownloadInfo info) {
        Language learning = info.learning();
        Language nativeLang = info.nativeLang();
        holder.nameText.setText(
                String.format(Locale.US, "Downloading %s-%s", learning.getDisplayName(), nativeLang.getDisplayName()));

        holder.progressBar.setProgress(info.getProgress());
        holder.radioButton.setVisibility(View.INVISIBLE);
        holder.deleteButton.setVisibility(View.GONE);
        holder.updateButton.setVisibility(View.GONE);
        holder.legacyIcon.setVisibility(View.GONE);
        holder.progressContainer.setVisibility(View.VISIBLE);
        holder.progressBar.setProgress(info.getProgress());
        holder.progressText.setText(info.getMbText());
        holder.itemView.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return dictionaries.size() + (activeDownload != null ? 1 : 0);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        RadioButton radioButton;
        TextView nameText;
        ImageButton deleteButton;
        ImageButton updateButton;
        ImageView legacyIcon;
        View progressContainer;
        ProgressBar progressBar;
        TextView progressText;

        ViewHolder(View itemView) {
            super(itemView);
            radioButton = itemView.findViewById(R.id.activeRadioButton);
            nameText = itemView.findViewById(R.id.dictionaryNameText);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            updateButton = itemView.findViewById(R.id.updateButton);
            legacyIcon = itemView.findViewById(R.id.legacyIcon);
            progressContainer = itemView.findViewById(R.id.downloadProgressContainer);
            progressBar = itemView.findViewById(R.id.downloadProgressBar);
            progressText = itemView.findViewById(R.id.progressText);
        }
    }
}
