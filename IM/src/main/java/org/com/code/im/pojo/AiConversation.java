package org.com.code.im.pojo;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {
    private long id;
    private String conversationId;
    private long userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
