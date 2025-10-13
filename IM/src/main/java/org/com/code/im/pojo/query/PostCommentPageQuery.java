package org.com.code.im.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentPageQuery {
    // 严格按照follow分页的字段命名和结构
    // 将分页查询中的ID字段序列化为字符串，避免JavaScript精度问题
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMaxId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMinId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long nextPage;
    
    // 帖子ID（用于评论查询）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;
    
    // 父评论ID（用于回复查询，null表示查询顶级评论）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;
} 