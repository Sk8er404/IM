package org.com.code.im.exception;

public class VideoParseException extends RuntimeException {
    public VideoParseException(String message) {
        super(message);
    }
    public String getMessage() {
        return super.getMessage();
    }
}
