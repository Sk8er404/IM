package org.com.code.im.controller.video;

import org.com.code.im.pojo.VideoComments;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.pojo.query.CommentPageQuery;
import org.com.code.im.pojo.dto.CommentPageResponse;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.service.video.VideoCommentService;
import org.com.code.im.utils.DFAFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/videoComment")
public class VideoCommentController {

    @Autowired
    private VideoCommentService videoCommentService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }
    String checkIfCommentIsValid(VideoComments addedComment){
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
    public ResponseHandler addComment(@RequestBody VideoComments addedComment ) {
        Long userId = getCurrentUserId();
        String errorMessage = checkIfCommentIsValid(addedComment);
        if(errorMessage!=null)
            return new ResponseHandler(HttpStatus.BAD_REQUEST.value(), errorMessage);
        /**
         * 评论内容过滤
         */
        addedComment.setContent(DFAFilter.filter(addedComment.getContent(),'*'));

        addedComment.setUserId(userId);
        Map map=videoCommentService.addComment(addedComment);

        //记录用户评论行为
        userBehaviourRecorder.recordAction(userId, ActionType.COMMENT, ContentType.VIDEO, addedComment.getVideoId());

        return new ResponseHandler(ResponseHandler.SUCCESS, "添加评论成功",map);
    }

    @GetMapping("/getCommentById/{commentId}")
    public ResponseHandler getCommentById(@PathVariable Long commentId) {
        VideoComments comment = videoCommentService.getCommentById(commentId);
        if (comment == null) {
            return new ResponseHandler(ResponseHandler.NOT_FOUND, "找不到该评论");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "获取评论成功", comment);
    }


    @PutMapping("/updateComment")
    public ResponseHandler updateComment(@RequestBody VideoComments commentRequest) {
        Long userId = getCurrentUserId();
        if(commentRequest.getContent()==null||commentRequest.getContent().isEmpty())
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"评论内容不能为空");
        /**
         * 评论内容过滤
         */
        commentRequest.setContent(DFAFilter.filter(commentRequest.getContent(),'*'));


        commentRequest.setUserId(userId);
        videoCommentService.updateComment(commentRequest);
        return new ResponseHandler(ResponseHandler.SUCCESS, "评论更新成功");
    }

    @DeleteMapping("/deleteComment/{commentId}")
    public ResponseHandler deleteComment(@PathVariable Long commentId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "请先登录");
        }
        videoCommentService.deleteComment(commentId, userId);

        //删除用户评论行为
        userBehaviourRecorder.deleteAction(userId, ActionType.COMMENT, ContentType.VIDEO, commentId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "删除评论成功");
    }

    /**
     * 获取视频评论列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/getCommentsByVideoIdWithCursor")
    public ResponseHandler getCommentsByVideoIdWithCursor(@RequestBody CommentPageQuery commentPageQuery) {
        try {
            if (commentPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            if (commentPageQuery.getVideoId() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "视频ID不能为空");
            }

            CommentPageResponse commentPageResponse = videoCommentService.getCommentsByVideoIdWithCursor(commentPageQuery);

            if (commentPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多评论");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", commentPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取评论回复列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/getRepliesByParentIdWithCursor")
    public ResponseHandler getRepliesByParentIdWithCursor(@RequestBody CommentPageQuery commentPageQuery) {
        try {
            if (commentPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            if (commentPageQuery.getVideoId() == null || commentPageQuery.getParentId() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "视频ID和父评论ID不能为空");
            }

            CommentPageResponse commentPageResponse = videoCommentService.getRepliesByParentIdWithCursor(commentPageQuery);

            if (commentPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多回复");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", commentPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

}
