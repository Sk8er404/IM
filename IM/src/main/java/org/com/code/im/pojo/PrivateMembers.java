package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PrivateMembers {

    // 将所有用户ID和会话ID序列化为字符串，避免JavaScript精度问题
    // 私人聊天成员表中的ID都是雪花算法生成的长整数
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long sessionId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long userId1;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long userId2;
}
