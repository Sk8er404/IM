package org.com.code.im.service.learning.impl;

import org.com.code.im.mapper.UserLearningProgressMapper;
import org.com.code.im.pojo.UserLearningProgress;
import org.com.code.im.pojo.enums.CompletionStatus;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.service.learning.UserLearningProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserLearningProgressImpl implements UserLearningProgressService {

    private static final Logger logger = LoggerFactory.getLogger(UserLearningProgressImpl.class);

    @Autowired
    private UserLearningProgressMapper userLearningProgressMapper;

    @Override
    @Transactional
    public void recordContentView(Long userId, Long contentId, ContentType contentType) {
        try {
            UserLearningProgress existingProgress = userLearningProgressMapper.findByUserIdAndContentIdAndContentType(userId, contentId, contentType);
            Timestamp currentTime = Timestamp.from(Instant.now());

            if (existingProgress != null) {
                existingProgress.setLastAccessTimestamp(currentTime);
                userLearningProgressMapper.updateUserLearningProgress(existingProgress);
                logger.info("用户 {} 对内容 {} ({}) 的学习进度已更新访问时间。", userId, contentId, contentType);
            } else {
                UserLearningProgress newProgress = new UserLearningProgress();
                newProgress.setUserId(userId);
                newProgress.setContentId(contentId);
                newProgress.setContentType(contentType);
                newProgress.setFirstAccessTimestamp(currentTime);
                newProgress.setLastAccessTimestamp(currentTime);
                newProgress.setDurationViewedSeconds(0);
                newProgress.setCompletionStatus(CompletionStatus.IN_PROGRESS);
                userLearningProgressMapper.insertUserLearningProgress(newProgress);
                logger.info("为用户 {} 创建了对内容 {} ({}) 的新学习进度记录。", userId, contentId, contentType);
            }
        } catch (Exception e) {
            logger.error("记录用户 {} 内容 {} ({}) 查看行为时发生错误: {}", userId, contentId, contentType, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> generateLearningReport(Long userId) {
        List<UserLearningProgress> progressList = userLearningProgressMapper.findByUserIdOrderByLastAccessTimestampDesc(userId);

        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("totalItemsAccessed", progressList.size());

        Map<ContentType, Long> countByType = progressList.stream()
                .collect(Collectors.groupingBy(UserLearningProgress::getContentType, Collectors.counting()));
        report.put("itemsCountByType", countByType);

        Map<CompletionStatus, Long> countByStatus = progressList.stream()
                .filter(p -> p.getCompletionStatus() != null)
                .collect(Collectors.groupingBy(UserLearningProgress::getCompletionStatus, Collectors.counting()));
        report.put("itemsCountByStatus", countByStatus);

        List<UserLearningProgress> recentActivities = progressList.stream().limit(5).collect(Collectors.toList());
        report.put("recentActivities", recentActivities);

        int totalDuration = progressList.stream()
                .mapToInt(p -> p.getDurationViewedSeconds() == null ? 0 : p.getDurationViewedSeconds())
                .sum();
        report.put("totalDurationViewedSeconds", totalDuration);

        logger.info("为用户 {} 生成学习报告成功。", userId);
        return report;
    }

    @Override
    public List<UserLearningProgress> getLearningProgressHistory(Long userId) {
        return userLearningProgressMapper.findByUserIdOrderByLastAccessTimestampDesc(userId);
    }

    @Override
    @Transactional
    public void updateLearningDuration(Long userId, Long contentId, ContentType contentType, int secondsViewed) {
        try {
            UserLearningProgress existingProgress = userLearningProgressMapper.findByUserIdAndContentIdAndContentType(userId, contentId, contentType);
            Timestamp currentTime = Timestamp.from(Instant.now());

            if (existingProgress != null) {
                existingProgress.setLastAccessTimestamp(currentTime);
                existingProgress.setDurationViewedSeconds((existingProgress.getDurationViewedSeconds() == null ? 0 : existingProgress.getDurationViewedSeconds()) + secondsViewed);
                userLearningProgressMapper.updateUserLearningProgress(existingProgress);
                logger.info("用户 {} 对内容 {} ({}) 的学习时长已更新，增加 {} 秒。", userId, contentId, contentType, secondsViewed);
            } else {
                UserLearningProgress newProgress = new UserLearningProgress();
                newProgress.setUserId(userId);
                newProgress.setContentId(contentId);
                newProgress.setContentType(contentType);
                newProgress.setFirstAccessTimestamp(currentTime);
                newProgress.setLastAccessTimestamp(currentTime);
                newProgress.setDurationViewedSeconds(secondsViewed);
                newProgress.setCompletionStatus(CompletionStatus.IN_PROGRESS);
                userLearningProgressMapper.insertUserLearningProgress(newProgress);
                logger.info("为用户 {} 创建了内容 {} ({}) 的学习记录并记录了初始时长 {} 秒。", userId, contentId, contentType, secondsViewed);
            }
        } catch (Exception e) {
            logger.error("更新用户 {} 内容 {} ({}) 学习时长时发生错误: {}", userId, contentId, contentType, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void markContentAsCompleted(Long userId, Long contentId, ContentType contentType) {
        try {
            UserLearningProgress existingProgress = userLearningProgressMapper.findByUserIdAndContentIdAndContentType(userId, contentId, contentType);
            Timestamp currentTime = Timestamp.from(Instant.now());

            if (existingProgress != null) {
                existingProgress.setLastAccessTimestamp(currentTime);
                existingProgress.setCompletionStatus(CompletionStatus.COMPLETED);
                userLearningProgressMapper.updateUserLearningProgress(existingProgress);
                logger.info("用户 {} 已将内容 {} ({}) 标记为已完成。", userId, contentId, contentType);
            } else {
                UserLearningProgress newProgress = new UserLearningProgress();
                newProgress.setUserId(userId);
                newProgress.setContentId(contentId);
                newProgress.setContentType(contentType);
                newProgress.setFirstAccessTimestamp(currentTime);
                newProgress.setLastAccessTimestamp(currentTime);
                newProgress.setDurationViewedSeconds(0);
                newProgress.setCompletionStatus(CompletionStatus.COMPLETED);
                userLearningProgressMapper.insertUserLearningProgress(newProgress);
                logger.info("为用户 {} 创建了内容 {} ({}) 的学习记录并直接标记为已完成。", userId, contentId, contentType);
            }
        } catch (Exception e) {
            logger.error("标记用户 {} 内容 {} ({}) 为已完成时发生错误: {}", userId, contentId, contentType, e.getMessage(), e);
        }
    }
}