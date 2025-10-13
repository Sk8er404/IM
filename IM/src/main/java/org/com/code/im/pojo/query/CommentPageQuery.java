package org.com.code.im.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageQuery {
    // 严格按照follow分页模式的字段顺序
    // 当前页最大ID（用于向后翻页）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMaxId;
    
    // 当前页最小ID（用于向前翻页）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long curPageMinId;
    
    // 翻页方向：正数向后翻页，负数向前翻页，0无效
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long nextPage;
    
    // 视频ID（用于评论查询）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long videoId;
    
    // 父评论ID（用于回复查询，null表示查询顶级评论）
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;
} 