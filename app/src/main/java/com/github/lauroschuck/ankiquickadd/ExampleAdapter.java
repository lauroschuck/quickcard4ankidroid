package com.github.lauroschuck.ankiquickadd;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ExampleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_EXAMPLE = 1;
    public static final int TYPE_CONTENT = 2;

    public static class Item {
        final int type;
        final String html;
        final String translationHtml; // Only for examples
        boolean selected;

        Item(int type, String html, String translationHtml) {
            this.type = type;
            this.html = html;
            this.translationHtml = translationHtml;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<Item> getSelectedItems() {
        List<Item> selected = new ArrayList<>();
        for (Item item : items) {
            if (item.type == TYPE_EXAMPLE && item.selected) {
                selected.add(item);
            }
        }
        return selected;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_header, parent, false));
        } else if (viewType == TYPE_EXAMPLE) {
            return new ExampleViewHolder(inflater.inflate(R.layout.item_example, parent, false));
        } else {
            return new ContentViewHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).title.setText(fromHtml(item.html));
        } else if (holder instanceof ContentViewHolder) {
            TextView tv = ((ContentViewHolder) holder).textView;
            tv.setText(fromHtml(item.html));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setPadding(32, 8, 32, 8);
        } else if (holder instanceof ExampleViewHolder) {
            ExampleViewHolder evh = (ExampleViewHolder) holder;
            evh.swedish.setText(fromHtml(item.html));
            evh.english.setText(fromHtml(item.translationHtml));
            evh.checkBox.setOnCheckedChangeListener(null);
            evh.checkBox.setChecked(item.selected);
            evh.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> item.selected = isChecked);
        }
    }

    private Spanned fromHtml(String source) {
        if (source == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(source);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        HeaderViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.headerTitle);
        }
    }

    static class ContentViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ContentViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }

    static class ExampleViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView swedish, english;
        ExampleViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.exampleCheckbox);
            swedish = itemView.findViewById(R.id.swedishText);
            english = itemView.findViewById(R.id.englishText);
        }
    }

    public static Item header(String html) {
        return new Item(TYPE_HEADER, html, null);
    }

    public static Item content(String html) {
        return new Item(TYPE_CONTENT, html, null);
    }

    public static Item example(String swedishHtml, String englishHtml) {
        return new Item(TYPE_EXAMPLE, swedishHtml, englishHtml);
    }
}
