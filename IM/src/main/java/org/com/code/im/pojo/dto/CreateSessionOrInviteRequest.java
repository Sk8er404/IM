package org.com.code.im.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.com.code.im.pojo.Sessions;

@Data
public class CreateSessionOrInviteRequest {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long ownerId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long[] userIds;
    
    private Sessions session;
    private String requestType;
}
