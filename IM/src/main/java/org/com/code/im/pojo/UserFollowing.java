package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowing {
    // 将用户ID和会话ID序列化为字符串，避免JavaScript精度问题
    // 用户关注列表中的ID都是雪花算法生成的长整数
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long id;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long sessionId;
    
    private String avatar;
    private String userName;

    public UserFollowing(long id, String avatar, String userName) {
        this.id = id;
        this.avatar = avatar;
        this.userName = userName;
    }
}
