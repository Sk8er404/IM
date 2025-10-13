package org.com.code.im.LangChain4j.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    @JsonProperty("conversation_id")
    String conversationId;

    @JsonProperty("user_id")
    long userId;

    @JsonProperty("summary")
    String summary;

    @JsonProperty("summary_embedding")
    float[] summaryEmbedding;

    @JsonProperty("original_question")
    String originalQuestion;

    @JsonProperty("original_answer")
    String originalAnswer;

    @JsonProperty("sequence_id")
    long sequenceId;
}
