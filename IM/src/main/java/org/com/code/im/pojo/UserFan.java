package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserFan {
    // 将用户ID和自增ID序列化为字符串，避免JavaScript精度问题
    // 用户ID是雪花算法生成的长整数，自增ID用于分页查询
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long id;
    
    private String avatar;
    private String userName;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long autoIncreasementId;
}
