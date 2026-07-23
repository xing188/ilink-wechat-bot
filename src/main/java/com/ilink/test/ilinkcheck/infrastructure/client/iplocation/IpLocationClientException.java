package com.ilink.test.ilinkcheck.infrastructure.client.iplocation;

public class IpLocationClientException extends RuntimeException {

    private final Type type;

    public IpLocationClientException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public IpLocationClientException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        INVALID_RESPONSE,
        UPSTREAM_ERROR
    }
}
