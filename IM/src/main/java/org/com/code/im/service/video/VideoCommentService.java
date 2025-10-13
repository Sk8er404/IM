package org.com.code.im.service.video;

import org.com.code.im.pojo.VideoComments;
import org.com.code.im.pojo.query.CommentPageQuery;
import org.com.code.im.pojo.dto.CommentPageResponse;

import java.util.Map;

public interface VideoCommentService {
    Map addComment(VideoComments addedComment);

    VideoComments getCommentById(Long commentId);

    void updateComment(VideoComments existingComment);

    void deleteComment(Long commentId, Long userId);

    CommentPageResponse getCommentsByVideoIdWithCursor(CommentPageQuery commentPageQuery);

    CommentPageResponse getRepliesByParentIdWithCursor(CommentPageQuery commentPageQuery);
}

