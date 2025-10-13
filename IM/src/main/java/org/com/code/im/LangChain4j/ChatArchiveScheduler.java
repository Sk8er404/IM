package org.com.code.im.LangChain4j;

import org.com.code.im.LangChain4j.Service.ChatMemoryService;
import org.com.code.im.LangChain4j.dto.ChatConversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ChatArchiveScheduler {
    @Autowired
    @Qualifier("objRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ChatMemoryService chatMemoryService;

    // 每分钟检查一次是否需要把用户的活跃会话归档
    @Scheduled(fixedRate = 60_000) // 1分钟
    public void archiveExpiredConversations(){

        long now = System.currentTimeMillis();
        // 从 ZSET 中找出所有 score <= now 的 conversationId（即该归档的）
        Set<Object> conversationsToArchive = redisTemplate.opsForZSet()
                .rangeByScore(ChatMemoryService.Key_User_ActiveConversationId_Scheduler, 0, now);

        if (conversationsToArchive == null || conversationsToArchive.isEmpty()) {
            return;
        }

        for (Object userId_conversationId : conversationsToArchive) {
            String[] userId_conversationId_array = userId_conversationId.toString().split("_");
            Long userId = Long.parseLong(userId_conversationId_array[0]);
            String conversationId = userId_conversationId_array[1];

            // 用户活跃会话的 key
            String key_User_ActiveConversation = String.format(ChatMemoryService.Key_User_ActiveConversation,userId);

            // 获取用户活跃会话
            List<Object> conversationList = redisTemplate.opsForHash().values(key_User_ActiveConversation);
            if(conversationList == null || conversationList.isEmpty()){
                return;
            }
            ChatConversation conversation = (ChatConversation) conversationList.get(0);
            // 将用户活跃的会话异步归档
            chatMemoryService.asyncDialogueIndexing(userId, conversation);

            // 删除用户活跃会话的redis缓存
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations ops) throws DataAccessException {
                    ops.multi();

                    ops.opsForZSet().remove(ChatMemoryService.Key_User_ActiveConversationId_Scheduler, userId_conversationId);
                    ops.opsForHash().delete(key_User_ActiveConversation, conversationId);

                    return ops.exec();
                }
            });
        }

    }
}
