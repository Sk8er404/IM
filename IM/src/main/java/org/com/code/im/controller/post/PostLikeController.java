package org.com.code.im.controller.post;

import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.post.PostLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/postLike")
public class PostLikeController {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/likePost/{postId}")
    public ResponseHandler likePost(@PathVariable Long postId) {
        Long userId = getCurrentUserId();
        postLikeService.likePost(postId, userId);

        //记录用户喜欢帖子的行为
        userBehaviourRecorder.recordAction(userId, ActionType.LIKE, ContentType.POST, postId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "点赞成功");
    }

    @DeleteMapping("/unlikePost/{postId}")
    public ResponseHandler unlikePost(@PathVariable Long postId) {
        Long userId = getCurrentUserId();
        postLikeService.unlikePost(postId, userId);

        //删除用户喜欢帖子的行为
        userBehaviourRecorder.deleteAction(userId, ActionType.LIKE, ContentType.POST, postId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "取消点赞成功");
    }

    @GetMapping("/queryLikedPostList/{pageNum}")
    public ResponseHandler queryLikedPostList(@PathVariable int pageNum) {
        Long userId = getCurrentUserId();
        List<Posts> postList= postLikeService.queryLikedPostList(userId,pageNum);
        if (postList==null)
            postList = new ArrayList<>();
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询自己喜欢的帖子成功",postList);
    }

}
