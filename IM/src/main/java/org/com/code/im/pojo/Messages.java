package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Messages {

  /**
   * 用户需要发送的消息格式,举例
   * {
   *     "sequenceId":1,
   *     "sessionId":1,
   *     "content":"hello",
   *     "messageType":"text"
   * }
   */
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long messageId;

  //维护消息顺序性和去重
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long sequenceId;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long sessionId;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long senderId;

  private String content;
  //messageType ENUM('text', 'image', 'file') DEFAULT 'text',
  private String messageType;
  /**
   * 数据库在上海,这个createdAt的时间是插入数据库的createdAt的时间,
   * 所以我使用ZoneId.of("Asia/Shanghai")来固定这个时区
   */
  private LocalDateTime createdAt;
  /**
   * 这是给前端处理用的时间戳
   * 因为时间戳（long 类型）不涉及时区问题
   */
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long timestamp;
}
