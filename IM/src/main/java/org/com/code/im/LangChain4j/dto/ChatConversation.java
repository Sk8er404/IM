package org.com.code.im.LangChain4j.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {
    private String conversationId;
    private List<Map<String, String>> messages;
    private String summary;

    public ChatConversation(String conversationId, List<Map<String, String>> messages) {
        this.conversationId = conversationId;
        this.messages = messages;
        this.summary = "无";
    }
}
