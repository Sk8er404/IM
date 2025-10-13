package org.com.code.im.controller.video;

import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.query.VideoPageQuery;
import org.com.code.im.pojo.dto.VideoPageResponse;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.learning.UserLearningProgressService;
import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.service.video.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@RestController
public class VideoController {
    @Autowired
    private VideoService videoService;

    @Autowired
    private UserLearningProgressService userLearningProgressService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    /**
     * 我的设计思路是前端通过调用任何除了/api/video/queryVideoDetail以外的其他接口,
     * 获取到的视频列表数据是不包括这些视频的URL,tags,description,我是想要懒加载和节省带宽
     * 所以只有用户在真正想要看某个视频的时候,前端需要通过/api/video/queryVideoDetail接口获取视频的URL码观看
     * 同时获取该视频的tags,description
     *
     * 所以这个视频用于从视频列表中获取某个视频的详细信息,用于观看视频，
     * 这个接口只能用于查询非
     */

    @GetMapping("/api/video/queryVideoDetail")
    public ResponseHandler queryVideoDetail(@RequestParam("id") long id) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        userLearningProgressService.recordContentView(userId, id, ContentType.VIDEO);

        //记录用户行为
        userBehaviourRecorder.recordAction(userId, ActionType.CLICK,ContentType.VIDEO, id);

        //记录用户看过哪些内容,在一段时间内,避免推荐重复的内容给用户
        userBehaviourRecorder.recordWhichContentUserHasViewed(userId,id,ContentType.VIDEO);

        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.queryVideoDetail(id));
    }


    @GetMapping("/api/video/searchVideoByKeyWords")
    public ResponseHandler searchVideoByHybridSearch(
            @RequestParam("keyWords") String keyWords,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {


        //记录用户行为
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        userBehaviourRecorder.recordSearchKeyword(userId, keyWords);

        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.searchVideoByHybridSearch(keyWords, page, size));
    }

    @GetMapping("/api/video/searchVideoByTime")
    public ResponseHandler searchVideoByTime(@RequestParam("startTime") String startTime,
                                            @RequestParam("endTime") String endTime,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {

        try {
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功",
                    videoService.searchVideoByTime(startTime, endTime, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "日期格式错误，请使用 yyyy-MM-dd 格式，如: 2025-05-14");
        }
    }

    /**
     * 当作者要查询自己的视频列表，哪些过审了，哪些在等待过审，哪些没有过审
     * 如果遇到上述任何一种情况，当遇到查询视频详细信息的时候，必须只能用这个接口查询
     *
     * /api/video/queryVideoDetail 接口和它不一样，如果是正常在视频主页看视频，查询视频详情
     * 就用这个接口
     *
     * 当如果是在用户主页查询自己视频的过审情况，则只能用/api/video/querySelfVideoDetail 查询自己视频的详情
     */
    @GetMapping("/api/video/querySelfVideoDetail")
    public ResponseHandler querySelfVideoDetail(@RequestParam("id") long id) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Map<String, Long> map= new HashMap();
        map.put("id",id);
        map.put("userId",userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.querySelfVideoDetail(map));
    }

    @GetMapping("/api/video/selectSelfVideoWaitToReview")
    public ResponseHandler selectSelfVideoWaitToReview() {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.selectSelfVideoWaitToReview(userId));
    }

    @GetMapping("/api/video/selectSelfApprovedVideo")
    public ResponseHandler selectSelfApprovedVideo() {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.selectSelfApprovedVideo(userId));
    }

    @GetMapping("/api/video/selectSelfRejectedVideo")
    public ResponseHandler selectSelfRejectedVideo() {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.selectSelfRejectedVideo(userId));
    }

    @GetMapping("/api/video/selectAllVideoWaitToReview")
    public ResponseHandler selectAllVideoWaitToReview() {
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoService.selectAllVideoWaitToReview());
    }

    @PutMapping("/api/video/updateVideoReviewStatus")
    public ResponseHandler updateVideoReviewStatus(@RequestParam("id") long id,
                                                  @RequestParam("status") String status,
                                                  @RequestParam(value = "reviewNotes",required = false) String reviewNotes) {
        long reviewerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        videoService.updateVideoReviewStatus(id, status, reviewerId, reviewNotes);
        return new ResponseHandler(ResponseHandler.SUCCESS, "更新成功");
    }

    /**
     * 获取最新视频列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/api/video/queryLatestVideosWithCursor")
    public ResponseHandler queryLatestVideosWithCursor(@RequestBody VideoPageQuery videoPageQuery) {
        try {
            if (videoPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            VideoPageResponse videoPageResponse = videoService.queryLatestVideosWithCursor(videoPageQuery);

            if (videoPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多视频");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门视频列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/api/video/queryMostViewedVideosWithCursor")
    public ResponseHandler queryMostViewedVideosWithCursor(@RequestBody VideoPageQuery videoPageQuery) {
        try {
            if (videoPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            VideoPageResponse videoPageResponse = videoService.queryMostViewedVideosWithCursor(videoPageQuery);

            if (videoPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多视频");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", videoPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }
}
