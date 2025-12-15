package com.example.myassistant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class Note implements Serializable {
    private final long id;
    private String title;
    private String content;
    private int color;
    private long lastModified;
    private boolean pinned;
    private long pinnedTimestamp;

    public Note(String title, String content, int color) {
        this.id = UUID.randomUUID().getMostSignificantBits();
        this.title = title;
        this.content = content;
        this.color = color;
        this.lastModified = System.currentTimeMillis();
        this.pinned = false;
        this.pinnedTimestamp = 0;
    }

    public Note(Note original) {
        this.id = original.id;
        this.title = original.title;
        this.content = original.content;
        this.color = original.color;
        this.lastModified = original.lastModified;
        this.pinned = original.pinned;
        this.pinnedTimestamp = original.pinnedTimestamp;
    }

    public long getId() {
        return id;
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

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return id == note.id &&
                color == note.color &&
                lastModified == note.lastModified &&
                pinned == note.pinned &&
                pinnedTimestamp == note.pinnedTimestamp &&
                Objects.equals(title, note.title) &&
                Objects.equals(content, note.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, content, color, lastModified, pinned, pinnedTimestamp);
    }
}
