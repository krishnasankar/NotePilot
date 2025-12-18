package com.example.myassistant;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Note implements Serializable {
    private List<ChecklistItem> checklist = new ArrayList<>();
    private boolean isChecklist = false;
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

public Note(String title, List<ChecklistItem> checklist, int color) {
    this.id = UUID.randomUUID().getMostSignificantBits();
    this.title = title;
    this.checklist = checklist;
    this.isChecklist = true;
    this.color = color;
    this.lastModified = System.currentTimeMillis();
    this.pinned = false;
    this.pinnedTimestamp = 0;
}

public Note(Note original) {
    this.id = original.id;
    this.title = original.title;
    this.content = original.content;
    this.checklist = new ArrayList<>(original.checklist);
    this.isChecklist = original.isChecklist;
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

public List<ChecklistItem> getChecklist() {
    return checklist;
}

public void setChecklist(List<ChecklistItem> checklist) {
    this.checklist = checklist;
    this.lastModified = System.currentTimeMillis();
}

public boolean isChecklist() {
    return isChecklist;
}

public void setChecklist(boolean checklist) {
    isChecklist = checklist;
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
                Objects.equals(content, note.content) &&
                isChecklist == note.isChecklist &&
                Objects.equals(checklist, note.checklist);
    }

    @Override
    public int hashCode() {
return Objects.hash(id, title, content, checklist, isChecklist, color, lastModified, pinned, pinnedTimestamp);
    }
}
