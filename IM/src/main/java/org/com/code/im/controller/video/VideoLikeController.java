package org.com.code.im.controller.video;

import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.service.video.VideoLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class VideoLikeController {
    @Autowired
    private VideoLikeService videoLikeService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    @PostMapping("api/videoLike/giveLikeToVideo/{videoId}")
    public ResponseHandler giveLikeToVideo(@PathVariable long videoId) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        videoLikeService.insertVideoLike(videoId, userId);

        // 记录用户行为
        userBehaviourRecorder.recordAction(userId, ActionType.LIKE, ContentType.VIDEO, videoId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "点赞成功");
    }

    @DeleteMapping("api/videoLike/cancelLikeToVideo/{videoId}")
    public ResponseHandler cancelLikeToVideo(@PathVariable long videoId) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        videoLikeService.deleteVideoLike(videoId, userId);

        // 删除用户行为
        userBehaviourRecorder.deleteAction(userId, ActionType.LIKE, ContentType.VIDEO, videoId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "取消点赞成功");
    }

    @GetMapping("api/videoLike/queryLikedVideoList/{pageNum}")
    public ResponseHandler queryLikedVideoList(@PathVariable int pageNum) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        List<Videos> videoList = videoLikeService.queryLikedVideoList(userId, pageNum);
        if (videoList==null) {
            videoList = new ArrayList<>();
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询自己喜欢的视频列表成功",videoList);
    }
}
