package org.com.code.im.exception;

public class ElasticSearchException extends RuntimeException {
    public ElasticSearchException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
