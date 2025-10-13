package org.com.code.im.exception;

public class AIModelException extends RuntimeException {
    public AIModelException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
