package com.example.myassistant;

import java.io.Serializable;

public class Note implements Serializable {
    private String title;
    private String content;
    private int color;

    public Note(String title, String content, int color) {
        this.title = title;
        this.content = content;
        this.color = color;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getColor() {
        return color;
    }
}
