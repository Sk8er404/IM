package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class VideoComments {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long id;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long videoId;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long userId;
  
  private String userName;
  private String content;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long parentId;
  
  private String replyTo;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long repliesCount;

  private LocalDateTime createdAt;
  
  // 新增autoIncreasementId字段，用于游标分页
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long autoIncreasementId;

  public Map toMap() {
    Map map = new HashMap();
    map.put("videoId", videoId != null ? videoId.toString() : null);
    map.put("content", content);
    map.put("parentId", parentId != null ? parentId.toString() : null);
    map.put("replyTo", replyTo);
    return map;
  }
}
