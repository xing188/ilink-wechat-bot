package com.ilink.test.ilinkcheck.infrastructure.client.news;

public class NewsClientException extends RuntimeException {

    private final Type type;

    public NewsClientException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public NewsClientException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        INVALID_PARAMETER,
        NOT_FOUND,
        SERVICE_UNAVAILABLE,
        UPSTREAM_ERROR
    }
}
