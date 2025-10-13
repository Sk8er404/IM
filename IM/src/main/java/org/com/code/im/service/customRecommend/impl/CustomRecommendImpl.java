package org.com.code.im.service.customRecommend.impl;

import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.config.Model;
import org.com.code.im.exception.RecommendException;
import org.com.code.im.mapper.PostMapper;
import org.com.code.im.mapper.VideoMapper;
import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.service.UpdateLatestLikeService;
import org.com.code.im.service.customRecommend.CustomRecommendService;
import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.utils.serializer.VectorSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CustomRecommendImpl implements CustomRecommendService {
    @Autowired
    UserBehaviourRecorder userBehaviourRecorder;
    @Autowired
    ElasticUtil elasticUtil;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private UpdateLatestLikeService updateLatestLikeService;

    @Autowired
    @Qualifier("redisTemplateByteArray")
    RedisTemplate<String, byte[]> redisTemplateByteArray;


    public List<Posts> getRecommendedPosts(Long userId,int number) {
        try{
            List<Long> ids=getRecommendedContentIds(userId,ContentType.POST,number);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = postMapper.selectPostListByManyIds(ids);
            List<Posts> postList = objectList.stream().map(obj -> (Posts) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(postList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RecommendException("推荐帖子失败");
        }
    }


    public List<Videos> getRecommendedVideos(Long userId,int number) {
        try{
            List<Long> ids=getRecommendedContentIds(userId,ContentType.VIDEO,number);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = videoMapper.selectVideoListByManyIds(ids);
            List<Videos> videoList = objectList.stream().map(obj -> (Videos) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(videoList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RecommendException("推荐视频失败");
        }
    }

    public List<Posts> getSimilarPosts(Long postId,int number) {
        try{
            List<Long> ids=getSimilarContentIds(postId,ElasticConfig.POST_AVERAGE_VECTOR_INDEX, number);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }

            List<Object> objectList = postMapper.selectPostListByManyIds(ids);
            List<Posts> postList = objectList.stream().map(obj -> (Posts) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(postList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RecommendException("推荐相似帖子失败");
        }
    }

    public List<Videos> getSimilarVideos(Long id,int number) {
        try{
            List<Long> ids=getSimilarContentIds(id,ElasticConfig.VIDEO_AVERAGE_VECTOR_INDEX, number);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }

            List<Object> objectList = videoMapper.selectVideoListByManyIds(ids);
            List<Videos> videoList = objectList.stream().map(obj -> (Videos) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(videoList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RecommendException("推荐相似视频失败");
        }
    }



    /**
     * 分别计算和存储 postProfileVector 和 videoProfileVector 。
     * 在推荐时动态计算最终画像:
     *
     * 场景一：要为用户推荐【帖子】时
     *
     * 赋予“帖子画像”更高的权重，赋予“视频画像”较低的权重。
     * finalVectorForPost = 0.7 * postProfileVector + 0.3 * videoProfileVector
     * 然后用这个 finalVectorForPost 去搜索相似帖子。
     *
     * 主要根据他的阅读品味来推荐文章，但同时也把他看视频的兴趣作为“灵感来源”，为他拓展一些新的阅读领域。
     *
     * 场景二：当您要为用户推荐【视频】时
     *
     * 赋予“视频画像”更高的权重，赋予“帖子画像”较低的权重。
     * finalVectorForVideo = 0.3 * postProfileVector + 0.7 * videoProfileVector
     * 然后用这个 finalVectorForVideo 去搜索相似视频。
     *
     * 主要根据他的观影品味来推荐视频，但同时也把他爱读的文章主题作为补充，为他推荐一些知识性或深度向的视频。
     *
     * 0.7和0.3是可调整的超参数，可以根据实际效果进行优化
     *
     *
     * 方法流程：
     * 引入Redis缓存：专门用于存储计算好的用户画像向量，设置15分钟TTL
     *
     * 获取用户向量的时候，先从缓存中获取，如果没有，则计算向量再缓存。
     *
     *
     * @param userId
     * @param contentType
     * @return
     * @throws IOException
     */

    @Override
    public List<Long> getRecommendedContentIds(Long userId, ContentType contentType,int number) throws IOException {

        float[] finalVector = new float[Model.DimensionOfEmbeddingModel];;

        String key = String.format(UserProfile_ContentType, userId, contentType.getType());
        if(redisTemplateByteArray.hasKey(key)){
            finalVector = VectorSerializer.deserialize(redisTemplateByteArray.opsForValue().get(key));

        }else{
            float[] postProfileVector = userBehaviourRecorder.getWeightedAvgVector(userId, ContentType.POST);
            float[] videoProfileVector = userBehaviourRecorder.getWeightedAvgVector(userId, ContentType.VIDEO);

            if(contentType == ContentType.POST){
                for (int i = 0; i < finalVector.length; i++) {
                    finalVector[i] = WEIGHT_POST_PROFILE_FOR_POST_RECOMMENDATION * postProfileVector[i] +
                            WEIGHT_VIDEO_PROFILE_FOR_POST_RECOMMENDATION * videoProfileVector[i];
                }
            }
            else{
                for (int i = 0; i < finalVector.length; i++) {
                    finalVector[i] = WEIGHT_POST_PROFILE_FOR_VIDEO_RECOMMENDATION * postProfileVector[i] +
                            WEIGHT_VIDEO_PROFILE_FOR_VIDEO_RECOMMENDATION * videoProfileVector[i];
                }
            }
            /**
             * 很重要!!!
             *
             * 全零向量的搜索限制
             * 问题的核心在于全零向量在数学上的一些特殊性，这直接影响了不同相似度算法的可用性。
             *
             * cosine（余弦相似度）：不支持
             * 这是最常见的限制情况。余弦相似度通过计算两个向量之间的夹角来衡量它们的相似性。在数学定义中，零向量（即所有元素都为0的向量）的模长为0，
             * 导致无法计算其单位向量，因此无法定义与其他向量的夹角。在Elasticsearch中，若索引字段的相似度度量设置为cosine，当索引或查询一个全零向量时，系统会抛出错误。
             *
             * l2_norm（L2范数，欧几里得距离）：支持
             * 如果您希望能够搜索全零向量，可以选择使用l2_norm作为相似度度量。欧几里得距离计算的是向量空间中两个点之间的直线距离。
             * 一个全零向量与另一个全零向量之间的距离为0。使用全零向量进行查询时，Elasticsearch可以有效地找到索引中其他与之距离相近（或相等）的向量。
             *
             * dot_product（点积）：不支持
             * 虽然点积本身可以计算全零向量（结果为0），但在Elasticsearch中，当dot_product用于实现优化的余弦相似度搜索时，
             * 它要求所有向量（包括文档向量和查询向量）都必须是单位向量（模长为1）。零向量的模长为0，无法被归一化为单位向量，
             * 因此不满足使用dot_product进行相似度搜索的前提条件。
             *
             * 因为我们的索引是 cosine 来比较向量相似度,因为零向量没有方向,  所以无法用它搜索相似的向量
             * 如果一个新用户登录,在有他的行为记录获取用户画像之前,他的向量我设置成零向量
             * 如果推荐系统检测到用户画像向量是零向量,则返回一个空的推荐内容的ID列表
             */

            boolean ifFinalVectorIsZero = true;
            for (int i = 0; i < finalVector.length; i++) {
                if(finalVector[1] !=0f ){
                    ifFinalVectorIsZero = false;
                    break;
                }
            }
            if(ifFinalVectorIsZero){
                return new ArrayList<>();
            }



            redisTemplateByteArray.opsForValue().set(key, VectorSerializer.serialize(finalVector), Duration.ofMinutes(UserProfile_ExpiredTime_minutes));
        }

        String indexName;
        if(ContentType.POST.equals(contentType)){
            indexName = ElasticConfig.POST_AVERAGE_VECTOR_INDEX;
        }else{
            indexName = ElasticConfig.VIDEO_AVERAGE_VECTOR_INDEX;
        }

        List<Long> excludeContentIdList = userBehaviourRecorder.getWhichContentUserHasViewed(userId,contentType);

        //系统存储你最近的 N 条已经看过的内容ID，个性化推荐的时候会把已经看过的内容排除
        List<String> excludeContentIdListStr = excludeContentIdList.stream().map(Object::toString).toList();

        return elasticUtil.searchContentBySimilarAverageVector(finalVector,indexName,number,excludeContentIdListStr);
    }


    @Override
    public List<Long> getRecommendUserIds(Long userId) {
        return null;
    }

    /**
     * 据用户当前看的内容，推荐相似内容
     * @param contentId
     * @param indexName
     * @param number
     * @return
     * @throws IOException
     */
    @Override
    public List<Long> getSimilarContentIds(Long contentId, String indexName,int number) throws IOException {
        //先获得指定的contentId对应的内容的向量
        List<float[]> vectorList =elasticUtil.multiGetVectorByIds(new ArrayList<>(List.of(contentId)),indexName);
        if(vectorList==null||vectorList.isEmpty()){
           return new ArrayList<>();
        }
        float[] array = vectorList.get(0);

        List<String> excludeContentIdList = new ArrayList<>(List.of(contentId.toString()));
        //使用向量进行相似性搜索
        return elasticUtil.searchContentBySimilarAverageVector(array,indexName,number,excludeContentIdList);
    }
}
