package org.com.code.im.service.customRecommend;

import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.enums.ContentType;

import java.io.IOException;
import java.util.List;

public interface CustomRecommendService {

    String UserProfile_ContentType = "user_%d:contentType_%s";

    // 用户画像向量在redis中的缓存的过期时间，一旦画像过期后，用户再次点击推荐帖子则会重新计算用户向量然后缓存到redis中
    int UserProfile_ExpiredTime_minutes = 1;

    // 推荐帖子时：帖子画像的主权重
    float WEIGHT_POST_PROFILE_FOR_POST_RECOMMENDATION = 0.7f;
    // 推荐帖子时：视频画像的辅助权重
    float WEIGHT_VIDEO_PROFILE_FOR_POST_RECOMMENDATION = 0.3f;

    // 推荐视频时：视频画像的主权重
    float WEIGHT_VIDEO_PROFILE_FOR_VIDEO_RECOMMENDATION = 0.7f;
    // 推荐视频时：帖子画像的辅助权重
    float WEIGHT_POST_PROFILE_FOR_VIDEO_RECOMMENDATION = 0.3f;

    // 推荐用户时候 : 帖子画像的主权重
    float WEIGHT_POST_PROFILE_FOR_USER_RECOMMENDATION = 0.7f;
    // 推荐用户时：视频画像的辅助权重
    float WEIGHT_VIDEO_PROFILE_FOR_USER_RECOMMENDATION = 0.3f;


    List<Posts> getRecommendedPosts(Long userId,int number);

    List<Videos> getRecommendedVideos(Long userId,int number);

    List<Posts> getSimilarPosts(Long id,int number);

    List<Videos> getSimilarVideos(Long id,int number);

    List<Long> getRecommendedContentIds(Long userId, ContentType contentType,int number) throws IOException;

    List<Long> getSimilarContentIds(Long id, String contentType,int number) throws IOException;

    /**
     * 在学习Spring Batch之前我先不实现这个接口方法
     *
     * 我想要使用Spring Batch设置一个定时任务异步执行，每天固定时间，从redis中获取用户的行为记录，然后批量计算用户画像
     * 最后批量保存到ElasticSearch的 User 索引的向量字段中，最后 推荐用户的时候就直接根据用户当前的画像来推荐类似向量画像的用户
     *
     * 虽然上述推荐用户不是实时的，但是可以节省计算资源，减轻服务器压力
     */
    List<Long> getRecommendUserIds(Long userId) throws IOException;


}
