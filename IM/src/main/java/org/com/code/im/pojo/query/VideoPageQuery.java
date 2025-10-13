package org.com.code.im.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class VideoPageQuery {
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
    
    // 查询类型：latest(最新视频) 或 mostViewed(热门视频)
    private String queryType;
} 