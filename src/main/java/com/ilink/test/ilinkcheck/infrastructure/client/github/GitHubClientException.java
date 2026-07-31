package com.ilink.test.ilinkcheck.infrastructure.client.github;

public class GitHubClientException extends RuntimeException {

    private final Type type;

    public GitHubClientException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public GitHubClientException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        INVALID_PARAMETER,
        NOT_FOUND,
        RATE_LIMITED,
        SERVICE_UNAVAILABLE,
        UPSTREAM_ERROR
    }
}
