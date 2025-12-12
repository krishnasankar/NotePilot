package com.example.myassistant;

import java.io.Serializable;

public class Note implements Serializable {
    private String title;
    private String content;
    private int color;
    private long lastModified;
    private boolean pinned;
    private long pinnedTimestamp;

    public Note(String title, String content, int color) {
        this.title = title;
        this.content = content;
        this.color = color;
        this.lastModified = System.currentTimeMillis();
        this.pinned = false;
        this.pinnedTimestamp = 0;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.lastModified = System.currentTimeMillis();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.lastModified = System.currentTimeMillis();
    }

    public int getColor() {
        return color;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinned) {
            this.pinnedTimestamp = System.currentTimeMillis();
        } else {
            this.pinnedTimestamp = 0;
        }
    }

    public long getPinnedTimestamp() {
        return pinnedTimestamp;
    }
}
