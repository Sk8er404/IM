package org.com.code.im.controller.post;

import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.post.PostCommentLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/postCommentLike")
public class PostCommentLikeController {

    @Autowired
    private PostCommentLikeService postCommentLikeService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/likePostComment/{postId}/{comment}")
    public ResponseHandler likePost(@PathVariable Long postId,@PathVariable Long comment) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "未认证，请登录");
        }
        postCommentLikeService.likeComment(postId,comment, userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "点赞成功");
    }

    @DeleteMapping("/unlikePost/{comment}")
    public ResponseHandler unlikePost(@PathVariable Long comment) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "未认证，请登录");
        }
        postCommentLikeService.unlikeComment(comment, userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "取消点赞成功");
    }
}
