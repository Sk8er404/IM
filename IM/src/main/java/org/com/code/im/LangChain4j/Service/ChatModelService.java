package org.com.code.im.LangChain4j.Service;


import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.com.code.im.LangChain4j.Agent;
import org.com.code.im.LangChain4j.dto.ChatConversation;
import org.com.code.im.exception.AIModelException;
import org.com.code.im.mapper.ChatMemoryMapper;
import org.com.code.im.utils.serializer.AIChatMessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ChatModelService {
    @Autowired
    @Qualifier("ToolAgent")
    Agent agent;

    @Autowired
    ChatMemoryService chatMemoryService;

    @Autowired
    @Qualifier("objRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ChatMemoryMapper chatMemoryMapper;


    public String sendMessage(Long userId,String conversationId, ChatMessage userMessage) throws IOException {

        String key_User_ConversationCachedIdZSet = String.format(ChatMemoryService.Key_User_ConversationCachedIdZSet, userId);
        String key_User_ActiveConversation = String.format(ChatMemoryService.Key_User_ActiveConversation, userId);
        ChatConversation chatConversation = (ChatConversation) redisTemplate.opsForHash().get(key_User_ActiveConversation, conversationId);

        if(chatConversation==null){
            if(redisTemplate.opsForZSet().rank(key_User_ConversationCachedIdZSet, conversationId) == null){
                String summary = chatMemoryMapper.getAiConversationSummaryById(conversationId);
                if(summary==null)
                    throw new AIModelException("该会话不存在,请创建新的对话");
            }
            List<ChatConversation> chatConversationList = chatMemoryService.getChatConversations(List.of(conversationId));
            chatConversation = chatConversationList.get(0);

            //先把旧的活跃会话的缓存记录删除
            redisTemplate.delete(key_User_ActiveConversation);
            //添加新的活跃会话记录
            redisTemplate.opsForHash().put(key_User_ActiveConversation, conversationId, chatConversation);
        }
        //获取当前窗口的所有历史消息
        List<ChatMessage> messages = AIChatMessageSerializer.fromJson(chatConversation);
        //添加用户问题
        messages.add(userMessage);

        String response = agent.chat(messages);

        chatMemoryService.addMessage(userId,conversationId, userMessage, new AiMessage(response));

        return response;
    }
}