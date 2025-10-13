package org.com.code.im.controller.post;

import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.pojo.PostComment;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.pojo.query.PostCommentPageQuery;
import org.com.code.im.pojo.dto.PostCommentPageResponse;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.post.PostCommentService;
import org.com.code.im.utils.DFAFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/postComment")
public class PostCommentController {

    @Autowired
    private PostCommentService postCommentService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    String checkIfCommentIsValid(PostComment addedComment){
        String errorMessage=null;
        if(addedComment.getContent()==null||addedComment.getContent().isEmpty())
            errorMessage="评论内容不能为空";
        if(addedComment.getContent().length()>500)
            errorMessage="评论内容不能超过500个字符";
        if(addedComment.getParentId()!=null&&(addedComment.getReplyTo()==null||addedComment.getReplyTo().isEmpty()))
            errorMessage="回复评论时，回复人信息不能为空";
        if(addedComment.getParentId()==null&&addedComment.getReplyTo()!=null)
            errorMessage="未回复他人评论时，回复人信息必须为空";

        return errorMessage;
    }

    @PostMapping("/addComment")
    public ResponseHandler addComment(@RequestBody PostComment addedComment) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "请先登录");
        }
        String errorMessage=checkIfCommentIsValid(addedComment);
        if(errorMessage!=null)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, errorMessage);

        /**
         * 评论内容过滤
         */
        addedComment.setContent(DFAFilter.filter(addedComment.getContent(),'*'));

        addedComment.setUserId(userId);
        Map result=postCommentService.addComment(addedComment);

        //记录用户评论行为
        userBehaviourRecorder.recordAction(userId, ActionType.COMMENT, ContentType.POST, addedComment.getPostId());

        return new ResponseHandler(ResponseHandler.SUCCESS, "评论添加成功",result);
    }

    @GetMapping("/getCommentById/{commentId}")
    public ResponseHandler getCommentById(@PathVariable Long commentId) {
        PostComment comment = postCommentService.getCommentById(commentId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "获取评论成功", comment);
    }

    @PutMapping("/updateComment")
    public ResponseHandler updateComment(@RequestBody PostComment commentRequest) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "请先登录");
        }
        if(commentRequest.getContent()==null||commentRequest.getContent().isEmpty())
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"评论内容不能为空");
        /**
         * 评论内容过滤
         */
        commentRequest.setContent(DFAFilter.filter(commentRequest.getContent(),'*'));

        commentRequest.setUserId(userId);
        postCommentService.updateComment(commentRequest);
        return new ResponseHandler(ResponseHandler.SUCCESS, "评论更新成功");
    }

    @DeleteMapping("/deleteComment/{commentId}")
    public ResponseHandler deleteComment(@PathVariable Long commentId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "请先登录");
        }
        postCommentService.deleteComment(commentId, userId);

        //删除用户评论行为
        userBehaviourRecorder.deleteAction(userId, ActionType.COMMENT, ContentType.POST, commentId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "删除评论成功");
    }
    
    /**
     * 使用游标分页获取帖子评论（顶级评论）
     * 替代传统的/getCommentsByPostId/{postId}接口，解决深度分页性能问题
     */
    @PostMapping("/getCommentsByPostIdWithCursor")
    public ResponseHandler getCommentsByPostIdWithCursor(@RequestBody PostCommentPageQuery commentPageQuery) {
        try {
            // 参数验证
            if (commentPageQuery.getPostId() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "帖子ID不能为空");
            }
            
            if (commentPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "nextPage参数不能为0");
            }
            
            PostCommentPageResponse response = postCommentService.getCommentsByPostIdWithCursor(commentPageQuery);
            return new ResponseHandler(ResponseHandler.SUCCESS, "获取帖子评论成功", response);
            
        } catch (IllegalArgumentException e) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "获取帖子评论失败");
        }
    }
    
    /**
     * 使用游标分页获取评论回复
     * 替代传统的/getRepliesByParentId/{postId}/{commentId}接口，解决深度分页性能问题
     */
    @PostMapping("/getRepliesByParentIdWithCursor")
    public ResponseHandler getRepliesByParentIdWithCursor(@RequestBody PostCommentPageQuery commentPageQuery) {
        try {
            // 参数验证
            if (commentPageQuery.getPostId() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "帖子ID不能为空");
            }
            
            if (commentPageQuery.getParentId() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "父评论ID不能为空");
            }
            
            if (commentPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "nextPage参数不能为0");
            }
            
            PostCommentPageResponse response = postCommentService.getRepliesByParentIdWithCursor(commentPageQuery);
            return new ResponseHandler(ResponseHandler.SUCCESS, "获取评论回复成功", response);
            
        } catch (IllegalArgumentException e) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "获取评论回复失败");
        }
    }
}
