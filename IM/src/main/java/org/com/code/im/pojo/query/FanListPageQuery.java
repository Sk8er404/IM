package org.com.code.im.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FanListPageQuery {
    // 将分页查询中的ID字段序列化为字符串，避免JavaScript精度问题
    // 这些ID用于分页查询的范围控制，需要保持精确性
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    long curPageMaxId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    long curPageMinId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    long nextPage;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Long userId;
}
