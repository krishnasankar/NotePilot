package com.example.myassistant;

import java.io.Serializable;
import java.util.Objects;

public class ChecklistItem implements Serializable {
    public String text;
    public boolean checked;

    public ChecklistItem(String text, boolean checked) {
        this.text = text != null ? text : "";
        this.checked = checked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChecklistItem that = (ChecklistItem) o;
        return checked == that.checked && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, checked);
    }
}
