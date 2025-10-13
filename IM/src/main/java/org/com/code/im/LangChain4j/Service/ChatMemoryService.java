package org.com.code.im.LangChain4j.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.Agent;
import org.com.code.im.LangChain4j.dto.ChatConversation;
import org.com.code.im.LangChain4j.dto.ChatSession;
import org.com.code.im.exception.ChatMemoryException;
import org.com.code.im.mapper.ChatMemoryMapper;
import org.com.code.im.pojo.AiConversation;
import org.com.code.im.pojo.enums.TimeZoneConfig;
import org.com.code.im.utils.serializer.AIChatMessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;

    @Autowired
    @Qualifier("tokenizer")
    Tokenizer tokenizer;
    @Autowired
    @Qualifier("objRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("ConversationSummaryAgent")
    Agent conversationSummaryAgent;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    @Qualifier("apiExecutor")
    private Executor apiExecutor; // 自定义的线程池

    @Autowired
    ChatMemoryMapper chatMemoryMapper;

    /**
     * redis 缓存会话消息的阈值，消息数量按轮算的
     * 一轮消息 == 用户的问题 和 模型的回答
     * 也就是如果 Max_Cached_RoundMessage_Count_Threshold ,则表示
     * <用户问，模型回答> 这一个来回 最多存储 10 个
     *
     * 以后这种 一问一答 的消息对统称为 一轮消息 RoundMessage
     */
    public static final int Max_Cached_RoundMessage_Count_Threshold = 8;
    /**
     * 一次性批量处理的消息数量，消息数量同上，也是按 轮算的，
     * 一轮消息 == 用户问题 和 模型回答
     *
     * Number_Of_RoundMessages_To_Index 必须小于 Max_Cached_RoundMessage_Count_Threshold !!!
     */
    public static final int Number_Of_RoundMessages_To_Index = 1;

    public static final long Expired_Time_Length = 12;//单位 小时
    public static final long Archive_Time_Length = 4 * 60 * 60 * 1000;//单位 毫秒

    public static final int Number_Of_Fetched_ConversationId_PageSize = 5;

    public static final int Number_Of_RoundMessage_To_Summarize = 5;
    public static final String Key_User_ActiveConversation = "chat-activeConversation:user_%d";
    public static final String Key_User_ConversationCachedIdZSet = "chat-cachedId:user_%d";

    public static final String Key_User_ActiveConversationId_Scheduler = "chat-activeConversationScheduler";

    /**
     * 分页获取用户窗口的消息列表（按时间顺序，从旧到新），页大小为 5
     */
    public List<ChatConversation> getConversationHistory(Long userId,int startPage) {
        try {
            int offset = (startPage - 1) * Number_Of_Fetched_ConversationId_PageSize;
            String cachedKey = String.format(Key_User_ConversationCachedIdZSet, userId);

            // 从缓存中获取会话ID
            List<String> conversationIds = redisTemplate.opsForZSet()
                    .reverseRange(cachedKey, offset, offset + Number_Of_Fetched_ConversationId_PageSize-1)
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());

            // 如果没有缓存，则从数据库中获取会话ID
            if(conversationIds==null||conversationIds.isEmpty()){
                List<AiConversation> aiConversationList = chatMemoryMapper.getAiConversation(Map.of("userId",userId, "offset",offset,"pageSize",Number_Of_Fetched_ConversationId_PageSize));
                conversationIds = aiConversationList.stream()
                        .map(AiConversation::getConversationId)
                        .collect(Collectors.toList());
            }

            return getChatConversations(conversationIds);

        } catch (Exception e) {
            e.printStackTrace();
            throw new ChatMemoryException("获取对话窗口内容失败");
        }
    }

    /**
     * 根据对话 Id 列表获取相关的全部对话内容
     * @param conversationIds
     * @return
     * @throws IOException
     */
    public List<ChatConversation> getChatConversations(List<String> conversationIds) throws IOException {
        // 查询这些会话ID对应的会话内容
        List<String> finalConversationIds = conversationIds;
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(ElasticConfig.USER_AI_DIALOGUE_MEMORY_INDEX)
                .query(q -> q
                        .terms(t -> t
                                .field("conversation_id")
                                .terms(tf -> tf.value(finalConversationIds.stream()
                                        .map(str -> co.elastic.clients.elasticsearch._types.FieldValue.of(str))
                                        .collect(Collectors.toList())))
                        )).size(1000).source(SourceConfig.of(sc ->
                        // 排除 summary 和 summary_embedding 两个字段
                        sc.filter(i ->i.excludes(List.of("summary","summary_embedding"))))
                ));

        /**
         * 第二个参数 ChatSession.class 告诉客户端：请把每条命中的文档（_source）反序列化成 ChatSession 对象
         */
        SearchResponse<ChatSession> response = client.search(searchRequest, ChatSession.class);

        Map<String, List<ChatSession>> groupedByConversationId = response.hits().hits().stream()
                .map(Hit::source)// Hit::source等价hit->hit.source()，提取出每一个 ChatSession
                //groupingBy 按照 conversationId 给这些 ChatSession 分组
                .collect(Collectors.groupingBy(ChatSession::getConversationId));

        return groupedByConversationId.entrySet().stream()
                .map(entry -> {
                    String convId = entry.getKey();
                    List<ChatSession> sessions = entry.getValue();
                    /**
                     * 因为从引擎中搜索出来的同一个会话窗口的多轮对话列表是乱序的, 所以这里需要排序
                     * 按照 ChatSession 的 sequenceId 从小到大排序,也就是按照对话的先后顺序进行排序
                     */
                    sessions.sort(Comparator.comparingLong(ChatSession::getSequenceId));
                    List<Map<String, String>> messages = AIChatMessageSerializer.chatSessionToMapList(sessions);
                    return new ChatConversation(convId, messages);
                })
                .collect(Collectors.toList());
    }

    /**
     * @param userId
     * @return
     *
     * @Transactional 注解只对支持事务的 Spring 管理的数据源（如 MySQL、PostgreSQL、Oracle 等关系型数据库）生效，
     * 对 Elasticsearch、Redis、大模型 API 调用等非事务性资源无效。
     */
    @Transactional
    public String createNewConversation(Long userId) {
        try {
            // 判断当前用户是否有未完成的对话窗口
            String key_User_ActiveConversation = String.format(Key_User_ActiveConversation, userId);

            List<Object> chatConversationList =  redisTemplate.opsForHash().values(key_User_ActiveConversation);

            String oldConversationId = null;
            if(chatConversationList != null&&!chatConversationList.isEmpty()){
                /**
                 * 如果用户新建了一个对话窗口后又再点了新建对话窗口，则不再新建窗口，直接返回该对话窗口的 ID
                 */
                if(((ChatConversation)chatConversationList.get(0)).getMessages().size()==0){
                    return ((ChatConversation)chatConversationList.get(0)).getConversationId();
                }
                // 如果有未完成的对话窗口，则进行索引，并且更新对话窗口的总结标题
                if(((ChatConversation)chatConversationList.get(0)).getMessages().size()>0){
                    ChatConversation chatConversation = (ChatConversation) chatConversationList.get(0);
                    oldConversationId =  chatConversation.getConversationId();

                    // 对用户的活跃话题进行异步归档
                    asyncDialogueIndexing(userId,chatConversation);

                    int messageListSize = chatConversation.getMessages().size();
                    int miniSize = Math.min(messageListSize, Number_Of_RoundMessage_To_Summarize * 2);
                    chatConversation.setMessages(new ArrayList<>(
                            chatConversation.getMessages().subList(messageListSize-miniSize, messageListSize)
                    ));

                    List<ChatMessage> messageWaitToSummarize = AIChatMessageSerializer.fromJson(chatConversation);
                    String title = conversationSummaryAgent.summaryConversation(messageWaitToSummarize);

                    chatMemoryMapper.updateAiConversation(Map.of("title",title));
                }
            }


            // 创建新对话窗口的UUID,将对话窗口添加到缓存中
            LocalDateTime localDateTime = LocalDateTime.now(TimeZoneConfig.SYSTEM_TIME_ZONE.getZoneId());
            String conversationId = java.util.UUID.randomUUID().toString();
            chatMemoryMapper.createAiConversation(Map.of("userId",userId,"conversationId",conversationId
            ,"createdAt",localDateTime,"updatedAt",localDateTime));

            String key_User_ConversationCachedIdZSet = String.format(Key_User_ConversationCachedIdZSet, userId);

            String finalOldConversationId = oldConversationId;
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations ops) throws DataAccessException {
                    // 开启事务
                    ops.multi();

                    // 存储对话对象到 Hash
                    ops.opsForHash().put(key_User_ActiveConversation, conversationId,
                            new ChatConversation(conversationId, new ArrayList<>()));

                    // 将 conversationId 加入缓存 ID 集合（ZSet）
                    long zSetScore = localDateTime
                            .atZone(TimeZoneConfig.SYSTEM_TIME_ZONE.getZoneId())
                            .toInstant()
                            .toEpochMilli();

                    ops.opsForZSet().add(key_User_ConversationCachedIdZSet, conversationId, zSetScore);

                    // 3. 刷新两个主键的过期时间
                    ops.expire(key_User_ActiveConversation, Expired_Time_Length, TimeUnit.HOURS);
                    ops.expire(key_User_ConversationCachedIdZSet, Expired_Time_Length, TimeUnit.HOURS);

                    // 更新新的会话“活跃对话过期时间”标记（用于后台归档判断）
                    ops.opsForZSet().add(Key_User_ActiveConversationId_Scheduler, userId+"_"+conversationId, System.currentTimeMillis() + Archive_Time_Length);

                    if(finalOldConversationId !=null){
                        // 删除旧的对话对象
                        ops.opsForHash().delete(key_User_ActiveConversation, finalOldConversationId);
                        // 删除旧的“活跃对话过期时间”标记
                        ops.opsForZSet().remove(Key_User_ActiveConversationId_Scheduler, userId+"_"+finalOldConversationId);
                    }


                    // 提交事务并返回结果
                    return ops.exec();
                }
            });

            return conversationId;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ChatMemoryException("创建新对话窗口失败");
        }
    }

    public void addMessage(Long userId,String conversationId, ChatMessage userMessage, ChatMessage aiMessage) {
        try {
            String key_User_ActiveConversation = String.format(Key_User_ActiveConversation, userId);
            String key_User_ConversationCachedIdZSet = String.format(Key_User_ConversationCachedIdZSet, userId);

            ChatConversation activeConversationHistory = (ChatConversation) redisTemplate.opsForHash().get(key_User_ActiveConversation, conversationId);

            //将新一轮对话的添加到窗口历史消息列表中
            List<Map<String, String>> messageList = AIChatMessageSerializer.toMapList(List.of(userMessage,aiMessage));

            if(activeConversationHistory.getMessages().size()< Max_Cached_RoundMessage_Count_Threshold *2){
                activeConversationHistory.getMessages().addAll(messageList);
            }else{
                ChatConversation historyConversationToIndex = new ChatConversation(conversationId, new ArrayList<>(
                        activeConversationHistory.getMessages().subList(0, Number_Of_RoundMessages_To_Index *2)
                ));
                asyncDialogueIndexing(userId,historyConversationToIndex);
                activeConversationHistory.getMessages().addAll(messageList);
                int size = activeConversationHistory.getMessages().size();
                activeConversationHistory = new ChatConversation(conversationId,new ArrayList<>(
                        activeConversationHistory.getMessages().subList(Number_Of_RoundMessages_To_Index *2, size)
                ));
            }

            ChatConversation finalActiveConversationHistory = activeConversationHistory;
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations ops) throws DataAccessException {
                    ops.multi();

                    ops.opsForHash().put(key_User_ActiveConversation, conversationId, finalActiveConversationHistory);
                    // 更新 活跃对话过期时间
                    ops.expire(key_User_ActiveConversation, Expired_Time_Length, TimeUnit.HOURS);
                    // 更新缓存 ID 集合（ZSet）的过期时间
                    ops.expire(key_User_ConversationCachedIdZSet, Expired_Time_Length, TimeUnit.HOURS);
                    // 更新会话的“活跃对话过期时间”标记（用于后台归档判断）
                    ops.opsForZSet().add(Key_User_ActiveConversationId_Scheduler, userId+"_"+conversationId, System.currentTimeMillis()+Archive_Time_Length);

                    return ops.exec();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            throw new ChatMemoryException("添加新消息失败");
        }
    }

    /**
     * @param userId
     * @param conversationId
     *
     * @Transactional 注解只对支持事务的 Spring 管理的数据源（如 MySQL、PostgreSQL、Oracle 等关系型数据库）生效，
     * 对 Elasticsearch、Redis、大模型 API 调用等非事务性资源无效。
     */
    @Transactional
    public void deleteConversation(Long userId, String conversationId) {
       try {
           //先删除redis中缓存的对话窗口
           String key_User_ActiveConversationHistory = String.format(Key_User_ActiveConversation, userId);
           redisTemplate.execute(new SessionCallback<Object>() {
               @Override
               public Object execute(RedisOperations operations) throws DataAccessException {
                   operations.multi();
                   operations.opsForHash().delete(key_User_ActiveConversationHistory, conversationId);
                   operations.opsForZSet().remove(String.format(Key_User_ConversationCachedIdZSet, userId), conversationId);
                   operations.opsForZSet().remove(Key_User_ActiveConversationId_Scheduler, userId+"_"+conversationId);
                   // 提交事务
                   return operations.exec();
               }
           });

           //删除数据库中缓存的对话窗口记录
           chatMemoryMapper.deleteAiConversation(Map.of("conversationId",conversationId,"userId",userId));

           // 删除ES中的对话窗口记录

           // 构建一个 DeleteByQuery 请求
           DeleteByQueryRequest deleteChunkIndex = DeleteByQueryRequest.of(d -> d
                   .index(ElasticConfig.USER_AI_DIALOGUE_MEMORY_INDEX)
                   .query(q -> q
                           // 使用 term query 来精确匹配 parentId
                           .term(t -> t
                                   .field("conversation_id")
                                   .value(conversationId)
                           )
                   )
                   // 设置 "wait_for_completion" 为 false，让其在后台执行，请求可以更快返回
                   // ES会启动一个任务来执行删除，对于大量文档的删除非常有用
                   .waitForCompletion(false)
                   // 发生冲突时继续执行
                   .conflicts(Conflicts.Proceed)
           );

           // 执行请求
           client.deleteByQuery(deleteChunkIndex);

       } catch (Exception e) {
           e.printStackTrace();
           throw new ChatMemoryException("删除对话窗口失败");
       }
    }

    /**
     * 裁剪消息，避免一次发送给AI模型的文本量过多
     */
    public String trimHistoryMessages(List<ChatMessage> messages) {
        try {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ChatMemoryException("裁剪消息失败");
        }
    }

    /**
     * 把传入的用户和AI的多轮对话列表，先把每轮对话总结，然后再把每轮的总结向量化，最后批量存储到Elasticsearch中
     * @param userId
     * @param messages
     */

    @Async("apiExecutor")
    public void asyncDialogueIndexing(long userId, ChatConversation messages) {
        try {
            String conversationId = messages.getConversationId();
            List<ChatMessage> messageList = AIChatMessageSerializer.fromJson(messages);
            List<String> textList = AIChatMessageSerializer.textList(messages);

            List<String> summarizedTextList = new ArrayList<>();

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for(int i = 0; i < messages.getMessages().size()/2; i++){
                int finalI = i*2;
                CompletableFuture<Void> future = CompletableFuture.runAsync(()->{
                    String summarizedText = conversationSummaryAgent.summaryConversation(messageList.subList(finalI,finalI+1));
                    summarizedTextList.add(summarizedText);
                }, apiExecutor).exceptionally(ex -> {
                    //如果API调用失败，无法获取摘要，则使用原始文本
                    List<ChatMessage> ms = messageList.subList(finalI,finalI+1);
                    String summarizedText =  "";
                    for (ChatMessage m : ms) {
                        if (m instanceof UserMessage) {
                            summarizedText += ((UserMessage) m).singleText();
                        }
                    }
                    summarizedTextList.add(summarizedText);
                    return null;
                });
                futures.add(future);
            }

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .join(); // 阻塞直到全部完成

            List<float[]> summarizedEmbeddingList = embeddingService.getEmbeddings(summarizedTextList);

            // 1. 创建一个BulkOperation列表
            List<BulkOperation> bulkOperations = new ArrayList<>();

            for (int i = 0,j = 0; i < messageList.size(); i=i+2) {
                Map<String, Object> doc = new HashMap<>();

                LocalDateTime localDateTime = LocalDateTime.now();
                long sequenceId = localDateTime
                        .atZone(TimeZoneConfig.SYSTEM_TIME_ZONE.getZoneId())
                        .toInstant()
                        .toEpochMilli();

                doc.put("conversation_id", conversationId);
                doc.put("user_id", userId);

                /**
                 * 每一轮对话的总结及其向量应该是总消息数messageList.size()的 二分之一
                 * 因为AI和用户的一问一答才算一轮对话
                 * 假设
                 *  AI->User->AI->Use->....
                 *  按照这种总共有20条消息,则总共有10轮对话,那么有10条总结,10条向量
                 *
                 *  所以这里的不能是i，而是单独用一个 j++ 变量给他们当索引
                 */
                doc.put("summary", summarizedTextList.get(j));
                doc.put("summary_embedding", summarizedEmbeddingList.get(j));

                doc.put("original_question", textList.get(i));
                doc.put("original_answer", textList.get(i+1));
                doc.put("sequence_id",sequenceId);
                j++;

                // 为每个文档创建一个 "index" 操作
                BulkOperation operation = BulkOperation.of(op -> op
                        .index(idx -> idx
                                .index(ElasticConfig.USER_AI_DIALOGUE_MEMORY_INDEX)
                                .id(userId+"_"+conversationId) // 这里需要一个唯一的ID
                                .document(doc)
                        )
                );
                bulkOperations.add(operation);
            }
            // 构建并执行 BulkRequest
            BulkRequest bulkRequest = BulkRequest.of(b -> b
                    .operations(bulkOperations)
            );

            client.bulk(bulkRequest);

        } catch (Exception e) {
            e.printStackTrace();
            throw new ChatMemoryException("异步对话索引失败");
        }
    }
}