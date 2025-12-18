package com.example.myassistant;

import java.io.Serializable;

public class ChecklistItem implements Serializable {
    public String text;
    public boolean checked;

    public ChecklistItem(String text, boolean checked) {
        this.text = text;
        this.checked = checked;
    }
}
