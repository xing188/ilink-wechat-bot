package com.ilink.test.ilinkcheck.application.reply;

public record ReplyDraft(Type type,
                         String text,
                         byte[] fileBytes,
                         String fileName,
                         String caption,
                         String fallbackText) {

    public enum Type {
        TEXT,
        FILE
    }

    public static ReplyDraft text(String text) {
        String normalized = text == null ? "" : text;
        return new ReplyDraft(Type.TEXT, normalized, null, null, null, normalized);
    }

    public static ReplyDraft file(byte[] fileBytes, String fileName, String caption, String fallbackText) {
        return new ReplyDraft(Type.FILE, null, fileBytes, fileName, caption, fallbackText);
    }
}
