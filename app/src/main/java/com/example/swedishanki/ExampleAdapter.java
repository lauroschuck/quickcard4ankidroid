package com.example.swedishanki;

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
    public static final int TYPE_TEXT = 2;

    public static class Item {
        final int type;
        final String text1;
        final String text2;
        boolean selected;

        Item(int type, String text1, String text2) {
            this.type = type;
            this.text1 = text1;
            this.text2 = text2;
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
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_TEXT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new TextViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_example, parent, false);
            return new ExampleViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).title.setText(item.text1);
        } else if (holder instanceof TextViewHolder) {
            ((TextViewHolder) holder).textView.setText(item.text1);
            ((TextViewHolder) holder).textView.setPadding(32, 8, 32, 8);
            ((TextViewHolder) holder).textView.setTextSize(14);
        } else if (holder instanceof ExampleViewHolder) {
            ExampleViewHolder evh = (ExampleViewHolder) holder;
            evh.swedish.setText(item.text1);
            evh.english.setText(item.text2);
            evh.checkBox.setOnCheckedChangeListener(null);
            evh.checkBox.setChecked(item.selected);
            evh.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> item.selected = isChecked);
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

    static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        TextViewHolder(View itemView) {
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

    public static Item header(String title) {
        return new Item(TYPE_HEADER, title, null);
    }

    public static Item text(String content) {
        return new Item(TYPE_TEXT, content, null);
    }

    public static Item example(String swedish, String english) {
        return new Item(TYPE_EXAMPLE, swedish, english);
    }
}
