package org.com.code.im.service.video;

import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.query.VideoPageQuery;
import org.com.code.im.pojo.dto.VideoPageResponse;

import java.util.List;
import java.util.Map;

public interface VideoService {
    Map insertVideo(Map map);
    int deleteVideo(long id,long userId);

    Map queryVideoDetail(long id);
    List<Videos> searchVideoByHybridSearch(String keyWords, int page, int size);
    List<Videos> searchVideoByTime(String startTime, String endTime,int page,int size);

    Map querySelfVideoDetail(Map map);
    List<Videos> selectSelfVideoWaitToReview(long userId);
    List<Videos> selectSelfApprovedVideo(long userId);
    List<Videos> selectSelfRejectedVideo(long userId);

    List<Videos> selectAllVideoWaitToReview();

    void updateVideoReviewStatus(long id, String status,long reviewerId,String reviewNotes);
    
    VideoPageResponse queryLatestVideosWithCursor(VideoPageQuery videoPageQuery);
    VideoPageResponse queryMostViewedVideosWithCursor(VideoPageQuery videoPageQuery);
}
