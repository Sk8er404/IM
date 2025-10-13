package org.com.code.im.utils.serializer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.com.code.im.LangChain4j.dto.ChatConversation;
import org.com.code.im.LangChain4j.dto.ChatSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;

public class AIChatMessageSerializer {

    public static List<ChatMessage> fromJson(ChatConversation messages){
        if(messages==null||messages.getMessages().isEmpty())
            return new ArrayList<>();


        return messages.getMessages().stream()
                .map(msg -> {
                    // 获取唯一的 key-value 对
                    Map.Entry<String, String> entry = msg.entrySet().iterator().next();
                    String role = entry.getKey();
                    String content = entry.getValue();

                    switch (role) {
                        case "user":
                            return new UserMessage(requireNonNullElse(content,""));
                        case "assistant":
                            return new AiMessage(requireNonNullElse(content,""));
                        case "system":
                            return new SystemMessage(requireNonNullElse(content,""));
                        default:
                            // 未知角色，默认当作用户消息，或可抛异常
                            return new UserMessage(requireNonNullElse(content,""));
                    }
                })
                .collect(Collectors.toList());
    }

    public static List<Map<String, String>> toMapList(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        return messages.stream().map(message -> {
           if (message instanceof UserMessage) {
               UserMessage userMessage = (UserMessage) message;
               return Map.of("user", userMessage.singleText());
           } else if (message instanceof AiMessage) {
               AiMessage aiMessage = (AiMessage) message;
               return Map.of("assistant", requireNonNullElse(aiMessage.text(), ""));
           } else if (message instanceof SystemMessage) {
               SystemMessage systemMessage = (SystemMessage) message;
               return Map.of("system", systemMessage.text());
           } else {
               return Map.of("unknown", "error");
           }
        }).collect(Collectors.toList());
    }

    public static List<String> textList(ChatConversation messages){
        if(messages==null||messages.getMessages().isEmpty())
            return new ArrayList<>();

        List<String> result = new ArrayList<>();
        int i=0;
        for (Map<String, String> message : messages.getMessages()) {
            // 获取 Map 中的第一个（也是唯一的）value
            if (!message.isEmpty()) {
                String text = message.values().iterator().next();
                if(i%2==0)
                    text = "user:"+text;
                else
                    text = "assistant:"+text;
                result.add(text);
                i++;
            }
            // 如果允许空 map，也可以跳过；否则可抛异常或记录日志
        }
        return result;
    }

    public static List<Map<String, String>> chatSessionToMapList(List<ChatSession> chatSessions) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatSession chatSession : chatSessions) {
            messages.add(Map.of("user", chatSession.getOriginalQuestion()));
            messages.add(Map.of("assistant", chatSession.getOriginalAnswer()));
        }
        return messages;
    }
}
