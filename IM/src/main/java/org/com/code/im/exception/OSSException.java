package org.com.code.im.exception;

public class OSSException extends RuntimeException {
    public OSSException(String message) {
        super(message);
    }

  @Override
  public String getMessage() {
    return super.getMessage();
  }
}
