package com.example.myassistant;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NoteModelTest {

    @Test
    public void testChecklistItemEqualsAndHashCode() {
        ChecklistItem item1 = new ChecklistItem("Buy groceries", false);
        ChecklistItem item2 = new ChecklistItem("Buy groceries", false);
        ChecklistItem item3 = new ChecklistItem("Buy groceries", true);
        ChecklistItem item4 = new ChecklistItem("Clean room", false);

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());

        assertNotEquals(item1, item3);
        assertNotEquals(item1, item4);
    }

    @Test
    public void testNoteColorSetterAndGetter() {
        Note note = new Note("Test Title", "Test Content", 0xFF123456);
        assertEquals(0xFF123456, note.getColor());

        note.setColor(0xFFAABBCC);
        assertEquals(0xFFAABBCC, note.getColor());

        // Test changing back to default black (0)
        note.setColor(0);
        assertEquals(0, note.getColor());
    }

    @Test
    public void testDefaultNoteColorIsZero() {
        Note note = new Note("Title", "Content", 0);
        assertEquals(0, note.getColor());
    }

    @Test
    public void testNoteChecklistEquality() {
        List<ChecklistItem> list1 = new ArrayList<>();
        list1.add(new ChecklistItem("Item 1", false));
        list1.add(new ChecklistItem("Item 2", true));

        List<ChecklistItem> list2 = new ArrayList<>();
        list2.add(new ChecklistItem("Item 1", false));
        list2.add(new ChecklistItem("Item 2", true));

        Note note1 = new Note("Tasks", list1, 0xFF112233);
        Note note2 = new Note(note1);

        assertEquals(note1, note2);
        assertTrue(note2.isChecklist());
        assertEquals(2, note2.getChecklist().size());

        // Modify an item in note2
        note2.getChecklist().get(0).checked = true;
        assertNotEquals(note1, note2);
    }

    @Test
    public void testNotePinnedStatus() {
        Note note = new Note("Pinned Note", "Content", 0);
        assertFalse(note.isPinned());

        note.setPinned(true);
        assertTrue(note.isPinned());
        assertTrue(note.getPinnedTimestamp() > 0);

        note.setPinned(false);
        assertFalse(note.isPinned());
    }
}
