package org.com.code.im.responseHandler;

import com.alibaba.fastjson.JSON;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ResponseHandler {
    public static final int SUCCESS = 200;
    public static final int PROCESSING = 102;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int SERVER_ERROR = 500;

    private int code;
    private String message;
    private Object data;
    private Object sequenceId;

    public ResponseHandler(int code, String message){
        this.code = code;
        this.message = message;
        this.data = null;
        this.sequenceId = null;
    }
    public ResponseHandler(int code, String message, Object data){
        this.code = code;
        this.message = message;
        this.data = data;
        this.sequenceId = null;
    }
    public String toJSONString(){
        return JSON.toJSONString(this);
    }
}
