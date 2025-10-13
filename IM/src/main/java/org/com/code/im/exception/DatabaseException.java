package org.com.code.im.exception;

public class DatabaseException extends RuntimeException {
    public DatabaseException(String message) {
        super(message);
    }

    public String getMessage() {
        return super.getMessage();
    }
}
