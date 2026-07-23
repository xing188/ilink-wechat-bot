package com.ilink.test.ilinkcheck.model;

public record ConversationResult(Type type, String content) {

    public enum Type {
        TEXT,
        DRAW_IMAGE
    }

    public static ConversationResult text(String content) {
        return new ConversationResult(Type.TEXT, content);
    }

    public static ConversationResult drawImage(String prompt) {
        return new ConversationResult(Type.DRAW_IMAGE, prompt);
    }
}
