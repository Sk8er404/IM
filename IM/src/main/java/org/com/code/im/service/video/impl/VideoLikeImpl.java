package org.com.code.im.service.video.impl;
import org.com.code.im.pojo.Videos;
import org.com.code.im.service.LikeOperationService;
import org.com.code.im.service.UpdateLatestLikeService;
import org.com.code.im.service.video.VideoLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VideoLikeImpl implements VideoLikeService {

    @Autowired
    @Qualifier("getVideoOperation")
    private LikeOperationService likeOperationService;

    @Autowired
    @Qualifier("Videos")
    private UpdateLatestLikeService updateLatestLikeService;

    /**
     * 我点赞和取消点赞的逻辑的前提,
     * redis作为缓存层,不存储所有视频的所有点赞记录,所有点赞记录由mysql存储
     * 用户进行的点赞和取消点赞的操作暂时存储在redis中,每隔一段时间把记录统一批量同步到mysql中,然后删除redis中的暂存的记录
     * 这就有一个问题,如果一个人很久之前就给一个视频点赞了,后来他想要取消点赞,但是redis只是暂时缓存他最近的点赞或取消点赞的操作,
     * 而不是很久以前的点赞记录,所以只根据redis的记录不知道用户是否已经给该视频点赞过,
     * 所以,我此处的逻辑如下:
     *
     */

    @Override
    @Transactional
    public void insertVideoLike(long videoId, long userId) {
        likeOperationService.insertLike(null,videoId, userId);
    }


    @Override
    @Transactional
    public void deleteVideoLike(long videoId, long userId) {
        likeOperationService.deleteLike(videoId, userId);
    }

    //pageNum从1开始,pageSize为10
    @Override
    public List<Videos> queryLikedVideoList(long userId, int pageNum) {
        List<?> rawList = likeOperationService.queryLikedList(userId, pageNum);
        if (rawList == null)
            return null;
        List<Videos> videoList = new ArrayList<>();
        for (Object obj : rawList) {
            if (obj instanceof Videos) {
                videoList.add((Videos) obj);
            }
        }
        updateLatestLikeService.updateObjectLikeCountList(videoList);
        return videoList;
    }

    /**
     * 每半小时同步一次点赞记录到mysql中,然后删除redis中的暂存的记录
     */
    @Scheduled(fixedRate = 1800000)
    public void synchronizeRedisAndMysql() {
        likeOperationService.synchronizeRedisAndMysql();
    }
}
