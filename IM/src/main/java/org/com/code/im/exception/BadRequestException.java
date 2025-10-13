package org.com.code.im.exception;

//该异常类通常在服务层或控制器中抛出，以响应客户端发送的无效请求，并返回相应的错误信息。
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }

    public String getMessage() {
        return super.getMessage();
    }
}