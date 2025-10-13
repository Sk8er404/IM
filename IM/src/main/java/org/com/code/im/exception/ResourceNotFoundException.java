package org.com.code.im.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public String getMessage() {
        return super.getMessage();
    }
}
