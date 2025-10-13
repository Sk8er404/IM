package org.com.code.im.service.recorder.impl;

import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.Service.EmbeddingService;
import org.com.code.im.LangChain4j.config.Model;
import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.utils.serializer.VectorSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class UserBehaviourRecorderImpl implements UserBehaviourRecorder {
    @Autowired
    @Qualifier("redisTemplateLong")
    RedisTemplate<String, Long> redisTemplate;

    @Autowired
    @Qualifier("strRedisTemplate")
    RedisTemplate<String, String> strRedisTemplate;

    @Autowired
    @Qualifier("redisTemplateByteArray")
    RedisTemplate<String, byte[]> redisTemplateByteArray;

    @Autowired
    EmbeddingService embeddingService;
    
    @Autowired
    ElasticUtil elasticUtil;

    @Override
    public List<Long> getActionIds(Long userId, ActionType action, ContentType contentType) {
        String key = String.format(User_Action_ContentType, userId, action.getType(), contentType.getType());
        return redisTemplate.opsForList().range(key, 0, action.getMaxSize() - 1);
    }

    /**
     * 记录用户行为数据
     * @param userId
     * @param action
     * @param content
     * @param id
     */
    @Override
    public void recordAction(Long userId, ActionType action, ContentType content, Long id) {
        String key = String.format(User_Action_ContentType, userId, action.getType(), content.getType());
        redisTemplate.execute(new SessionCallback<Void>() {
            @Override
            public <K, V>Void execute(RedisOperations<K, V> operations) throws DataAccessException {
                // 在方法内部，进行安全的强制类型转换
                RedisOperations<String, Long> typedOperations = (RedisOperations<String, Long>) operations;
                // 开启事务,保证数据一致性
                typedOperations.multi();

                // 1. 将新项推入列表头部
                typedOperations.opsForList().leftPush(key, id);

                // 2. 修剪列表，只保留最新的 maxSize 个元素
                // 即使队列长度小于 maxSize-1，trim 操作也不会产生负面影响。它只是确保列表不会超过指定大小，但如果列表本来就比指定大小小，它不会做任何事情。
                typedOperations.opsForList().trim(key, 0, action.getMaxSize() - 1);

                // 执行事务
                typedOperations.exec();

                return null;
            }
        });
    }

    /**
     * 删除用户行为数据
     * @param userId
     * @param action
     * @param contentType
     * @param id
     */
    public void deleteAction(Long userId, ActionType action, ContentType contentType, Long id) {
        String key = String.format(User_Action_ContentType, userId, action.getType(), contentType.getType());
        redisTemplate.opsForList().remove(key, 1, id);
    }

    /**
     * 记录用户搜索关键词
     * @param userId
     * @param keyword
     */

    @Override
    @Async
    public void recordSearchKeyword(Long userId, String keyword) {
        String key = String.format(User_Action, userId, ActionType.SEARCH_KEYWORD.getType());
        strRedisTemplate.execute(new SessionCallback<Void>() {
            @Override
            public <K, V>Void execute(RedisOperations<K, V> operations) throws DataAccessException {
                // 在方法内部，进行安全的强制类型转换
                RedisOperations<String, String> typedOperations = (RedisOperations<String, String>) operations;
                // 开启事务,保证数据一致性
                typedOperations.multi();

                // 1. 将新项推入列表头部
                typedOperations.opsForList().leftPush(key, keyword);

                // 2. 修剪列表，只保留最新的 maxSize 个元素
                // 即使队列长度小于 maxSize-1，trim 操作也不会产生负面影响。它只是确保列表不会超过指定大小，但如果列表本来就比指定大小小，它不会做任何事情。
                typedOperations.opsForList().trim(key, 0, ActionType.SEARCH_KEYWORD.getMaxSize() - 1);

                /**
                 * 如果缓存层没有用户搜素关键词的向量，则调用AIModel获取向量，然后保存到缓存层中,缓存层关键词设置过期时间为1天
                 */
                Boolean hasKey = redisTemplateByteArray.hasKey(keyword);
                if(hasKey == null || !hasKey){
                    float[] embedding = embeddingService.getEmbedding(keyword);
                    redisTemplateByteArray.opsForValue().set(keyword, VectorSerializer.serialize(embedding),Duration.ofDays(Keywords_ExpiredTime_Days));
                }

                // 执行事务
                typedOperations.exec();

                return null;
            }
        });
    }

    /**
     * 获取用户搜索关键词的向量
     * @param userId
     * @return
     */
    @Override
    public List<float[]> getSearchKeywordsVectors(Long userId) {
        String key = String.format(User_Action, userId, ActionType.SEARCH_KEYWORD.getType());
        List<String> keywords = strRedisTemplate.opsForList().range(key, 0, -1);
        List<float[]> vectors = new ArrayList<>();

        if(keywords == null || keywords.size() == 0){
            float[] zeroVector = new float[Model.DimensionOfEmbeddingModel];
            vectors.add(zeroVector);
            return vectors;
        }


        for (String keyword : keywords) {
            Boolean hasKey = redisTemplateByteArray.hasKey(keyword);
            if(hasKey == null || !hasKey){
                float[] embedding = embeddingService.getEmbedding(keyword);
                redisTemplateByteArray.opsForValue().set(keyword, VectorSerializer.serialize(embedding), Duration.ofDays(Keywords_ExpiredTime_Days));
                vectors.add(embedding);
            }else{
                vectors.add(VectorSerializer.deserialize(redisTemplateByteArray.opsForValue().get(keyword)));
            }
        }
        return vectors;
    }

    /**
     * 获取用户 喜欢，评论，点击 3个行为类别的帖子向量(不同行为类别的内容列表数量不一样，向量权重也不一样)
     *
     * 用户最新前15个喜欢的内容,权重5
     * 用户最新前15个评论的内容,权重3
     * 用户最新前15个点击的内容,权重1
     * 用户最新前5个搜索的关键词,权重为7
     *
     * 然后根据这些向量求出加权后的平均向量即为这个用户在 视频 或者 帖子 等等这些内容的 的兴趣向量画像
     * @param userId
     * @param contentType
     * @return float[]
     */
    @Override
    public float[] getWeightedAvgVector(Long userId, ContentType contentType) throws IOException {
        String indexName;
        if(ContentType.POST.equals(contentType)){
            indexName = ElasticConfig.POST_AVERAGE_VECTOR_INDEX;
        }else {
            indexName = ElasticConfig.VIDEO_AVERAGE_VECTOR_INDEX;
        }
        // 获取用户喜欢行为的内容向量
        List<Long> likedContentIds = getActionIds(userId, ActionType.LIKE, contentType);
        // 新用户的likedContentIds为空，elasticUtil.multiGetVectorByIds方法会返回一个
        List<float[]> likedVectors = elasticUtil.multiGetVectorByIds(likedContentIds, indexName);
        
        // 获取用户评论行为的内容向量
        List<Long> commentedContentIds = getActionIds(userId, ActionType.COMMENT, contentType);
        List<float[]> commentedVectors = elasticUtil.multiGetVectorByIds(commentedContentIds, indexName);
        
        // 获取用户点击行为的内容向量
        List<Long> clickedContentIds = getActionIds(userId, ActionType.CLICK, contentType);
        List<float[]> clickedVectors = elasticUtil.multiGetVectorByIds(clickedContentIds, indexName);
        
        // 获取用户搜索关键词的向量
        List<float[]> searchKeywordVectors = getSearchKeywordsVectors(userId);

        // 计算加权平均向量
        return calculateWeightedAverageVector(likedVectors,commentedVectors,clickedVectors,searchKeywordVectors);
    }


    /**
     * 计算加权平均向量
     * @param likedVectors
     * @param commentedVectors
     * @param clickedVectors
     * @param searchKeywordVectors
     * @return
     */

    private float[] calculateWeightedAverageVector(
            List<float[]> likedVectors,
            List<float[]> commentedVectors,
            List<float[]> clickedVectors,
            List<float[]> searchKeywordVectors
    ) {
        int dimension = Model.DimensionOfEmbeddingModel;
        float[] sumVector = new float[dimension];

        // 加权累加
        for(float[] vector : likedVectors){
            for(int i = 0; i < dimension; i++){
                sumVector[i] += vector[i]*WEIGHT_LIKE;
            }
        }
        for(float[] vector : commentedVectors){
            for(int i = 0; i < dimension; i++){
                sumVector[i] += vector[i]*WEIGHT_COMMENT;
            }
        }
        for(float[] vector : clickedVectors){
            for(int i = 0; i < dimension; i++){
                sumVector[i] += vector[i]*WEIGHT_CLICK;
            }
        }
        for(float[] vector : searchKeywordVectors){
            for(int i = 0; i < dimension; i++){
                sumVector[i] += vector[i]*WEIGHT_SEARCH_KEYWORDS;
            }
        }

        // 计算总权重
        double totalWeight =
                WEIGHT_LIKE * likedVectors.size() +
                        WEIGHT_COMMENT * commentedVectors.size() +
                        WEIGHT_CLICK * clickedVectors.size() +
                        WEIGHT_SEARCH_KEYWORDS * searchKeywordVectors.size();

        // 归一化为加权平均
        for(int i = 0; i < dimension; i++){
            sumVector[i] /= totalWeight;
        }

        return sumVector;
    }

    @Override
    public void recordWhichContentUserHasViewed(Long userId, Long videoId, ContentType contentType){
        String key = String.format(User_ViewedContentType, userId, contentType.getType());
        if(redisTemplate.opsForList().size(key)>=MAX_VIEWED_CONTENT_SIZE){
            redisTemplate.opsForList().rightPop(key);
        }
        redisTemplate.opsForList().leftPush(key,videoId);
    }

    @Override
    public List<Long> getWhichContentUserHasViewed(Long userId, ContentType contentType) {
        return redisTemplate.opsForList().range(String.format(User_ViewedContentType, userId, contentType.getType()),0,-1);
    }
}