package org.com.code.im.service;

import org.com.code.im.pojo.Likeable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UpdateLatestLikeService {

    @Qualifier("objRedisTemplate")
    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    public static String ObjectList;

    @Bean("Videos")
    public UpdateLatestLikeService getVideoLikeCount() {
        UpdateLatestLikeService updateLatestLikeService = new UpdateLatestLikeService();
        updateLatestLikeService.ObjectList = "VideoList";
        return updateLatestLikeService;
    }
    @Bean("Posts")
    public UpdateLatestLikeService getPostLikeCount() {
        UpdateLatestLikeService updateLatestLikeService = new UpdateLatestLikeService();
        updateLatestLikeService.ObjectList = "PostList";
        return updateLatestLikeService;
    }
    @Bean("PostComments")
    public UpdateLatestLikeService getPostCommentLikeCount() {
        UpdateLatestLikeService updateLatestLikeService = new UpdateLatestLikeService();
        updateLatestLikeService.ObjectList = "PostCommentList";
        return updateLatestLikeService;
    }
    public <T extends Likeable> T updateObjectLikeCount(T object){
        if(object == null)
            return null;
        // 安全获取 Redis 中的点赞数
        Double likeScore = redisTemplate.opsForZSet().score(UpdateLatestLikeService.ObjectList, object.getId());
        long likeCount = (likeScore != null ? likeScore.longValue() : 0L);
        object.setLikeCount(object.getLikeCount() + likeCount);
        return object;
    }

    public <T extends Likeable> List<T> updateObjectLikeCountList(List<T> objects) {
        if (objects == null || objects.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> idList = objects.stream().map(T::getId).toList();
        List<Long> likeCountDelta= getLatestObjectLikeCount(idList);
        for (int i = 0; i < objects.size(); i++) {
            objects.get(i).setLikeCount(likeCountDelta.get(i)+objects.get(i).getLikeCount());
        }
        return objects;
    }

    public List<Long> getLatestObjectLikeCount(List<Long> ids){
        List<Object> countList =redisTemplate.executePipelined(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                byte[] key = redisTemplate.getStringSerializer().serialize(ObjectList);
                for (Long id : ids) {
                    byte[] idSerialized = redisTemplate.getStringSerializer().serialize(id.toString());
                    connection.zScore(key, idSerialized);
                }
                return null;
            }
        });
        return countList.stream()
                .map(count -> count == null ? 0L : ((Number) count).longValue())
                .toList();
    }
}
