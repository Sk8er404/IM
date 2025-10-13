package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.LearningPlan;

import java.util.List;

@Mapper
public interface LearningPlanMapper{

    void savePlan(LearningPlan learningPlan);

    List<LearningPlan> findByUserId(Long userId);

    LearningPlan findByIdAndUserId(Long id, Long userId);

    List<LearningPlan> findAllActivePlans(long  userId);

    void deleteByUserIdAndId(Long userId, Long id);

    void updatePlan(LearningPlan learningPlan);
} 