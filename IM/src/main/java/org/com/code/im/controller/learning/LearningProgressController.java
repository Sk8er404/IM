package org.com.code.im.controller.learning;

import org.com.code.im.pojo.UserLearningProgress;
import org.com.code.im.pojo.dto.LearningDurationUpdateRequest;
import org.com.code.im.pojo.dto.MarkContentAsCompletedRequest;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.learning.UserLearningProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningProgressController {

    private static final Logger logger = LoggerFactory.getLogger(LearningProgressController.class);

    @Autowired
    private UserLearningProgressService userLearningProgressService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/report")
    public ResponseHandler getLearningReport() {
        Long userId = getCurrentUserId();
        try {
            Map<String, Object> report = userLearningProgressService.generateLearningReport(userId);
            logger.info("用户 {} 请求学习报告成功。", userId);
            return new ResponseHandler(ResponseHandler.SUCCESS, "学习报告获取成功。", report);
        } catch (Exception e) {
            logger.error("为用户 {} 生成学习报告时发生异常: {}", userId, e.getMessage(), e);
            return new ResponseHandler(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取学习报告失败，请稍后重试。");
        }
    }

    @GetMapping("/history")
    public ResponseHandler getLearningHistory() {
        Long userId = getCurrentUserId();
        try {
            List<UserLearningProgress> history = userLearningProgressService.getLearningProgressHistory(userId);
            logger.info("用户 {} 请求学习历史成功。", userId);
            return new ResponseHandler(ResponseHandler.SUCCESS, "学习历史获取成功。", history);
        } catch (Exception e) {
            logger.error("为用户 {} 获取学习历史时发生异常: {}", userId, e.getMessage(), e);
            return new ResponseHandler(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取学习历史失败，请稍后重试。");
        }
    }

    @PostMapping("/duration")
    public ResponseHandler updateLearningDuration(@RequestBody LearningDurationUpdateRequest request) {
        Long userId = getCurrentUserId();
        if (request.getContentId() == null || request.getContentType() == null || request.getSecondsViewed() < 0) {
             return new ResponseHandler(HttpStatus.BAD_REQUEST.value(), "请求参数不合法。contentId, contentType不能为空, secondsViewed不能小于0。");
        }
        try {
            userLearningProgressService.updateLearningDuration(userId, request.getContentId(), request.getContentType(), request.getSecondsViewed());
            logger.info("用户 {} 更新内容 {} ({}) 的学习时长 {} 秒成功。", userId, request.getContentId(), request.getContentType(), request.getSecondsViewed());
            return new ResponseHandler(ResponseHandler.SUCCESS, "学习时长更新成功。");
        } catch (Exception e) {
            logger.error("用户 {} 更新内容 {} ({}) 的学习时长时发生异常: {}", userId, request.getContentId(), request.getContentType(), e.getMessage(), e);
            return new ResponseHandler(HttpStatus.INTERNAL_SERVER_ERROR.value(), "更新学习时长失败，请稍后重试。");
        }
    }

    @PostMapping("/complete")
    public ResponseHandler markContentAsCompleted(@RequestBody MarkContentAsCompletedRequest request) {
        Long userId = getCurrentUserId();
        if (request.getContentId() == null || request.getContentType() == null) {
            return new ResponseHandler(HttpStatus.BAD_REQUEST.value(), "请求参数不合法。contentId和contentType不能为空。");
        }
        try {
            userLearningProgressService.markContentAsCompleted(userId, request.getContentId(), request.getContentType());
            logger.info("用户 {} 标记内容 {} ({}) 为已完成成功。", userId, request.getContentId(), request.getContentType());
            return new ResponseHandler(ResponseHandler.SUCCESS, "内容标记为已完成。");
        } catch (Exception e) {
            logger.error("用户 {} 标记内容 {} ({}) 为已完成时发生异常: {}", userId, request.getContentId(), request.getContentType(), e.getMessage(), e);
            return new ResponseHandler(HttpStatus.INTERNAL_SERVER_ERROR.value(), "标记完成状态失败，请稍后重试。");
        }
    }
} 