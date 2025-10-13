package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.LearningTask;

import java.time.LocalTime;
import java.util.List;

@Mapper
public interface LearningTaskMapper{

    void saveTask(LearningTask learningTask);

    List<LearningTask> findByPlanIdAndUserId(Long planId, Long userId);

    LearningTask findByIdAndUserId(Long id, Long userId);

    List<LearningTask> selectTodayOnGoingTask(Long userId,List<Long> planIds);

    int resetAllTasksCompletionStatus();

    List<LearningTask> findByReminderEnabledTrueAndReminderTimeBetweenAndIsCompletedTodayFalse(LocalTime start, LocalTime end);

    List<LearningTask> findByUserId(Long userId);

    void deleteByUserIdAndId(Long userId, Long id);

    void updateTask(LearningTask learningTask);
} 