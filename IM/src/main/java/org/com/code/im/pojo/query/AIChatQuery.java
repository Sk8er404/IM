package org.com.code.im.pojo.query;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AIChatQuery {
    public String conversationId;
    public String userQuestion;
}
