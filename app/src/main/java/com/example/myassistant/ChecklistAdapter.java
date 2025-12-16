package com.example.myassistant;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.ChecklistViewHolder> {

    private final List<ChecklistItem> items;

    public ChecklistAdapter(List<ChecklistItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ChecklistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checklist, parent, false);
        return new ChecklistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChecklistViewHolder holder, int position) {
        ChecklistItem item = items.get(position);
        holder.editTextItem.setText(item.text);
        holder.checkBoxItem.setChecked(item.checked);
if (item.checked) {
    holder.editTextItem.setPaintFlags(holder.editTextItem.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
} else {
    holder.editTextItem.setPaintFlags(holder.editTextItem.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
}

holder.checkBoxItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
    item.checked = isChecked;
    if (isChecked) {
        holder.editTextItem.setPaintFlags(holder.editTextItem.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    } else {
        holder.editTextItem.setPaintFlags(holder.editTextItem.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }
});

        holder.buttonDeleteItem.setOnClickListener(v -> {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size());
        });

        holder.editTextItem.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                item.text = s.toString();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItem(ChecklistItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    static class ChecklistViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBoxItem;
        EditText editTextItem;
        ImageButton buttonDeleteItem;

        public ChecklistViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBoxItem = itemView.findViewById(R.id.checkBoxItem);
            editTextItem = itemView.findViewById(R.id.editTextItem);
            buttonDeleteItem = itemView.findViewById(R.id.buttonDeleteItem);
        }
    }
}
