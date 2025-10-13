package org.com.code.im.exception;

public class RecommendException extends RuntimeException {
    public RecommendException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
