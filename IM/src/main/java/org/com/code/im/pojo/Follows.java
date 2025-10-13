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
public class Follows {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long userId;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long fanId;
  
  private LocalDateTime createdAt;
}
