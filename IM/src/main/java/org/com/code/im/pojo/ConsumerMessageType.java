package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerMessageType {
    // 将消息中的ID字段序列化为字符串，避免JavaScript精度问题
    // 会话ID和用户ID都是雪花算法生成的长整数
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    long sessionId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    long userId;
    
    String type;
}
