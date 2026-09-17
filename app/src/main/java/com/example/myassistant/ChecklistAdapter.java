package com.example.myassistant;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.List;

public class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final List<ChecklistItem> items;
    private final OnItemAddedListener listener;
    private OnChecklistChangeListener changeListener;

    public interface OnItemAddedListener {
        void onItemAdded();
    }

    public interface OnChecklistChangeListener {
        void onChecklistChanged();
    }

    public ChecklistAdapter(List<ChecklistItem> items, OnItemAddedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setOnChecklistChangeListener(OnChecklistChangeListener changeListener) {
        this.changeListener = changeListener;
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
            ChecklistViewHolder checklistHolder = (ChecklistViewHolder) holder;
            ChecklistItem item = items.get(position);

            // Detach listeners before setting values to avoid firing during recycling
            checklistHolder.detachListeners();

            checklistHolder.editTextItem.setText(item.text);
            checklistHolder.checkBoxItem.setChecked(item.checked);
            updateStrikeThrough(checklistHolder.editTextItem, item.checked);

            // Set up check listener
            checklistHolder.checkListener = (buttonView, isChecked) -> {
                int currentPos = checklistHolder.getBindingAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                    ChecklistItem currentItem = items.get(currentPos);
                    currentItem.checked = isChecked;
                    updateStrikeThrough(checklistHolder.editTextItem, isChecked);
                    if (changeListener != null) {
                        changeListener.onChecklistChanged();
                    }
                }
            };
            checklistHolder.checkBoxItem.setOnCheckedChangeListener(checklistHolder.checkListener);

            // Set up text change listener
            checklistHolder.textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    int currentPos = checklistHolder.getBindingAdapterPosition();
                    if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                        items.get(currentPos).text = s.toString();
                        if (changeListener != null) {
                            changeListener.onChecklistChanged();
                        }
                    }
                }
            };
            checklistHolder.editTextItem.addTextChangedListener(checklistHolder.textWatcher);

            // Delete item listener with correct dynamic position
            checklistHolder.buttonDeleteItem.setOnClickListener(v -> {
                int currentPos = checklistHolder.getBindingAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                    items.remove(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, getItemCount() - currentPos);

                    if (changeListener != null) {
                        changeListener.onChecklistChanged();
                    }

                    // Hide keyboard if no more items
                    if (items.isEmpty()) {
                        checklistHolder.editTextItem.clearFocus();
                        InputMethodManager imm = (InputMethodManager) holder.itemView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(checklistHolder.editTextItem.getWindowToken(), 0);
                        }
                    }
                }
            });

            // Enter key listener to add a new item
            checklistHolder.editTextItem.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_NEXT ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
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

    private void updateStrikeThrough(EditText editText, boolean checked) {
        if (checked) {
            editText.setPaintFlags(editText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setAlpha(0.6f);
        } else {
            editText.setPaintFlags(editText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for the add item row
    }

    public void addItem(ChecklistItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
        notifyItemChanged(items.size()); // Update the add item position
        if (changeListener != null) {
            changeListener.onChecklistChanged();
        }
    }

    public void addItemAndFocus(ChecklistItem item, RecyclerView recyclerView) {
        items.add(item);
        int newPos = items.size() - 1;
        notifyItemInserted(newPos);
        notifyItemChanged(items.size()); // Update the add item position
        if (changeListener != null) {
            changeListener.onChecklistChanged();
        }

        // Scroll and focus on new item
        recyclerView.post(() -> {
            recyclerView.smoothScrollToPosition(items.size());
            recyclerView.postDelayed(() -> {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(newPos);
                if (holder instanceof ChecklistViewHolder) {
                    ChecklistViewHolder cvh = (ChecklistViewHolder) holder;
                    cvh.editTextItem.requestFocus();
                    cvh.editTextItem.setSelection(cvh.editTextItem.getText().length());
                    InputMethodManager imm = (InputMethodManager) recyclerView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(cvh.editTextItem, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 100);
        });
    }

    static class ChecklistViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox checkBoxItem;
        EditText editTextItem;
        ImageButton buttonDeleteItem;
        TextWatcher textWatcher;
        CompoundButton.OnCheckedChangeListener checkListener;

        public ChecklistViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBoxItem = itemView.findViewById(R.id.checkBoxItem);
            editTextItem = itemView.findViewById(R.id.editTextItem);
            buttonDeleteItem = itemView.findViewById(R.id.buttonDeleteItem);
        }

        void detachListeners() {
            if (textWatcher != null) {
                editTextItem.removeTextChangedListener(textWatcher);
                textWatcher = null;
            }
            checkBoxItem.setOnCheckedChangeListener(null);
            checkListener = null;
        }
    }

    static class AddItemViewHolder extends RecyclerView.ViewHolder {
        public AddItemViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
