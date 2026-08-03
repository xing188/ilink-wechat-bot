package com.ilink.test.ilinkcheck.model;

public record ConversationResult(Type type, String content, String fileCacheKey, String fileName) {

    public enum Type {
        TEXT,
        DRAW_IMAGE,
        /**
         * 已由工作流生成好的图片，content 为图片缓存 key，由发送层读取字节后直接发送。
         * 可选附带一个文件（fileCacheKey 为文件字节缓存 key，fileName 为下载文件名），
         * 例如思维导图同时发送 .xmind 可编辑文件。
         */
        IMAGE
    }

    public static ConversationResult text(String content) {
        return new ConversationResult(Type.TEXT, content, null, null);
    }

    public static ConversationResult drawImage(String prompt) {
        return new ConversationResult(Type.DRAW_IMAGE, prompt, null, null);
    }

    /**
     * 由 Agent 工作流生成并缓存的图片。content 为缓存 key，供发送层取回字节。
     */
    public static ConversationResult image(String cacheKey) {
        return new ConversationResult(Type.IMAGE, cacheKey, null, null);
    }

    /**
     * 图片 + 附带可下载文件（如思维导图 .xmind）。
     */
    public static ConversationResult imageWithFile(String cacheKey, String fileCacheKey, String fileName) {
        return new ConversationResult(Type.IMAGE, cacheKey, fileCacheKey, fileName);
    }
}
