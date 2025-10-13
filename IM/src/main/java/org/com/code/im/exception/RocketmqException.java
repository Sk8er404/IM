package org.com.code.im.exception;

public class RocketmqException extends RuntimeException {
    public RocketmqException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
