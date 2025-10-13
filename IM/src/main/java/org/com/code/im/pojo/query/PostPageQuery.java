package org.com.code.im.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.com.code.im.pojo.Posts;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostPageQuery {
    // 严格按照follow分页的字段命名和结构
    // 将分页查询中的ID字段序列化为字符串，避免JavaScript精度问题
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMaxId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMinId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long nextPage;
    
    // 用户ID（用于按用户查询帖子）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    
    // 帖子类型（用于按类型查询帖子）
    private Posts.PostType postType;
} 