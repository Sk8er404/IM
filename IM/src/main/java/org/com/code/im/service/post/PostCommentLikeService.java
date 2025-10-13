package org.com.code.im.service.post;

public interface PostCommentLikeService {
    void likeComment(Long postId,Long commentId, Long userId);
    void unlikeComment(Long commentId, Long userId);
}
