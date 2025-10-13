package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PrivateMemberQueryHandler {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long sessionId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long userId;
    
    private String userName;
    private String avatar;
}
