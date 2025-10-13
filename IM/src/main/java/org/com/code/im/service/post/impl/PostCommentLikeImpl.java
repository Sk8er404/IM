package org.com.code.im.service.post.impl;

import org.com.code.im.service.LikeOperationService;
import org.com.code.im.service.post.PostCommentLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCommentLikeImpl implements PostCommentLikeService {
    @Autowired
    @Qualifier("getPostCommentOperation")
    private LikeOperationService likeOperationService;

    @Override
    @Transactional
    public void likeComment(Long postId,Long commentId, Long userId) {
        likeOperationService.insertLike(postId,commentId, userId);
    }

    @Override
    @Transactional
    public void unlikeComment(Long postId, Long userId) {
        likeOperationService.deleteLike(postId, userId);
    }

    /**
     * 每半小时同步一次点赞记录到mysql中,然后删除redis中的暂存的记录
     */
    @Scheduled(fixedRate = 1800000)
    public void synchronizeRedisAndMysql() {
        likeOperationService.synchronizeRedisAndMysql();
    }
}
