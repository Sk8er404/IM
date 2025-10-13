package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.Videos;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoMapper{
    void insertVideo(Map map);

    int deleteVideo(long id,long userId);

    int deleteAllVideoLikePfOneVideo(long videoId);

    int deleteAllCommentOfOneVideo(long videoId);

    Videos queryVideoDetail(long id);

    void increaseViewCount(long id);

    Videos querySelfVideoDetail(Map map);

    List<Videos> selectSelfVideoWaitToReview(long userId);

    List<Videos> selectSelfApprovedVideo(long userId);

    List<Videos> selectSelfRejectedVideo(long userId);

    List<Videos> selectAllVideoWaitToReview();

    void updateVideoReviewStatus(Map map);

    String getUrl(long id);

    List<Object> selectVideoListByManyIds(List<Long> ids);
    
    /**
     * 获取视频最小autoIncreasementId，用于初始化分页查询
     */
    Long selectVideoMinAutoIncrementId();
    
    /**
     * 最新视频游标分页 - 向后翻页
     */
    List<Videos> queryLatestVideosAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 最新视频游标分页 - 向前翻页
     */
    List<Videos> queryLatestVideosAndNavigateToPreviousPageByIdRange(Map map);
    
    /**
     * 热门视频游标分页 - 向后翻页
     */
    List<Videos> queryMostViewedVideosAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 热门视频游标分页 - 向前翻页
     */
    List<Videos> queryMostViewedVideosAndNavigateToPreviousPageByIdRange(Map map);
}
