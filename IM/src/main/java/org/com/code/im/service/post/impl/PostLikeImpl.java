package org.com.code.im.service.post.impl;

import org.com.code.im.pojo.Posts;
import org.com.code.im.service.LikeOperationService;
import org.com.code.im.service.post.PostLikeService;
import org.com.code.im.service.UpdateLatestLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PostLikeImpl implements PostLikeService {

    @Autowired
    @Qualifier("getPostOperation")
    private LikeOperationService likeOperationService;

    @Autowired
    @Qualifier("Posts")
    UpdateLatestLikeService updateLatestLikeService;

    @Override
    @Transactional
    public void likePost(Long postId, Long userId) {
        likeOperationService.insertLike(null,postId, userId);
    }

    @Override
    @Transactional
    public void unlikePost(Long postId, Long userId) {
        likeOperationService.deleteLike(postId, userId);
    }

    //pageNum从1开始,pageSize为10
    @Override
    public List<Posts> queryLikedPostList(long userId, int pageNum) {
        List<?> rawList = likeOperationService.queryLikedList(userId, pageNum);
        if (rawList == null)
            return null;
        List<Posts> postList = new ArrayList<>();
        for (Object obj : rawList) {
            if (obj instanceof Posts) {
                postList.add((Posts) obj);
            }
        }
        updateLatestLikeService.updateObjectLikeCountList(postList);
        return postList;
    }

    /**
     * 每半小时同步一次点赞记录到mysql中,然后删除redis中的暂存的记录
     */
    @Scheduled(fixedRate = 1800000)
    public void synchronizeRedisAndMysql() {
        likeOperationService.synchronizeRedisAndMysql();
    }
}
