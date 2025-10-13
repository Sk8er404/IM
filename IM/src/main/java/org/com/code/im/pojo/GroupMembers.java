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
public class GroupMembers {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long sessionId;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long userId;
  
  private String nickName;
  @JSONField(format = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime joinedTime;
  private String role;

}
