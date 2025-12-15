package com.example.myassistant;

public class ChatMessage {
    public enum Author {
        USER, MODEL
    }

    private final String content;
    private final Author author;

    public ChatMessage(String content, Author author) {
        this.content = content;
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public Author getAuthor() {
        return author;
    }
}
