package com.ilink.test.ilinkcheck.infrastructure.client.book;

/**
 * 图书 API 请求失败异常。
 */
public class BookClientException extends RuntimeException {

    public BookClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public BookClientException(String message) {
        super(message);
    }
}