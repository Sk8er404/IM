package org.com.code.im.pojo;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Sessions {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long sessionId;

  private String sessionType;
  private String groupAvatar;
  private String groupName;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long ownerId;

  @JSONField(format = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createdAt;
}
