package org.com.code.im.exception;

public class ErrorResponse {
    private int status;
    private String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    // getter 和 setter 方法
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}