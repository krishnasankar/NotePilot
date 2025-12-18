package com.example.myassistant;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final List<ChecklistItem> items;
    private final OnItemAddedListener listener;

    public interface OnItemAddedListener {
        void onItemAdded();
    }

    public ChecklistAdapter(List<ChecklistItem> items, OnItemAddedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) {
            return VIEW_TYPE_ADD;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ADD) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_add_checklist, parent, false);
            return new AddItemViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checklist, parent, false);
            return new ChecklistViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChecklistViewHolder) {
            ChecklistItem item = items.get(position);
            ChecklistViewHolder checklistHolder = (ChecklistViewHolder) holder;
            checklistHolder.editTextItem.setText(item.text);
            checklistHolder.checkBoxItem.setChecked(item.checked);
            if (item.checked) {
                checklistHolder.editTextItem.setPaintFlags(checklistHolder.editTextItem.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                checklistHolder.editTextItem.setPaintFlags(checklistHolder.editTextItem.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            checklistHolder.checkBoxItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.checked = isChecked;
                if (isChecked) {
                    checklistHolder.editTextItem.setPaintFlags(checklistHolder.editTextItem.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    checklistHolder.editTextItem.setPaintFlags(checklistHolder.editTextItem.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            });

            checklistHolder.buttonDeleteItem.setOnClickListener(v -> {
                items.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, getItemCount() - position);

                // Clear focus and hide keyboard when deleting an item
                checklistHolder.editTextItem.clearFocus();
                InputMethodManager imm = (InputMethodManager) holder.itemView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(checklistHolder.editTextItem.getWindowToken(), 0);
            });

            checklistHolder.editTextItem.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    item.text = s.toString();
                }
            });

            // Add Enter key listener to create new item when Enter is pressed
            checklistHolder.editTextItem.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    // Create new checklist item
                    if (listener != null) {
                        listener.onItemAdded();
                    }
                    return true;
                }
                return false;
            });
        } else if (holder instanceof AddItemViewHolder) {
            AddItemViewHolder addHolder = (AddItemViewHolder) holder;
            addHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemAdded();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for the add item
    }

    public void addItem(ChecklistItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
        notifyItemChanged(items.size()); // Update the add item position
    }

    public void addItemAndFocus(ChecklistItem item, RecyclerView recyclerView) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
        notifyItemChanged(items.size()); // Update the add item position

        // Focus on the newly added item and scroll to make it visible
        recyclerView.post(() -> {
            // Smooth scroll to show the newly added item and keep the add button visible
            recyclerView.smoothScrollToPosition(items.size()); // Scroll to the add button position

            // Focus on the newly added item
            recyclerView.post(() -> {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(items.size() - 1);
                if (holder instanceof ChecklistViewHolder) {
                    ((ChecklistViewHolder) holder).editTextItem.requestFocus();
                    ((ChecklistViewHolder) holder).editTextItem.setSelection(((ChecklistViewHolder) holder).editTextItem.getText().length());
                    InputMethodManager imm = (InputMethodManager) recyclerView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(((ChecklistViewHolder) holder).editTextItem, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        });
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

    static class AddItemViewHolder extends RecyclerView.ViewHolder {
        public AddItemViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
