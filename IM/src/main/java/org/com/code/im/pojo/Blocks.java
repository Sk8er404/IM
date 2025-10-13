package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Blocks {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long blockerId;
  
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long blockedId;
  
  private String blockedName;
  private LocalDateTime blockedAt;

}
