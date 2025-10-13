package org.com.code.im.exception;

public class ChatMemoryException extends RuntimeException {
    public ChatMemoryException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
