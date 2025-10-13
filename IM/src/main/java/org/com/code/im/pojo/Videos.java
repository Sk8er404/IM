package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Videos extends Likeable{

  //private long id;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long userId;

  private String userName;
  private String title;
  private String url;
  private long views;

  //private long likeCount;

  private long commentCount;
  private List<String> tags;
  private String category;
  private double duration;
  private String description;
  private LocalDateTime createdAt;
  private String status;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long reviewerId;

  private LocalDateTime reviewedAt;
  private String reviewNotes;
  
  // 新增autoIncreasementId字段，用于游标分页
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long autoIncreasementId;

  public Map toMap() {
    Map<String, Object> map = new HashMap();
    map.put("id", id != null ? id.toString() : null);
    map.put("userId", String.valueOf(userId));
    map.put("title", title);
    map.put("url", url);
    map.put("views", views);
    map.put("likes", likeCount);
    map.put("tags", tags);
    map.put("category", category);
    map.put("duration", duration);
    map.put("description", description);
    map.put("createdAt", createdAt);
    return map;
  }
}