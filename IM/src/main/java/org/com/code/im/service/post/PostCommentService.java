package org.com.code.im.service.post;

import org.com.code.im.pojo.PostComment;
import org.com.code.im.pojo.query.PostCommentPageQuery;
import org.com.code.im.pojo.dto.PostCommentPageResponse;

import java.util.Map;

public interface PostCommentService {

    Map addComment(PostComment addedComment);

    PostComment getCommentById(Long commentId);

    void updateComment(PostComment existingComment);

    void deleteComment(Long commentId, Long userId);

    PostCommentPageResponse getCommentsByPostIdWithCursor(PostCommentPageQuery commentPageQuery);

    PostCommentPageResponse getRepliesByParentIdWithCursor(PostCommentPageQuery commentPageQuery);
}
