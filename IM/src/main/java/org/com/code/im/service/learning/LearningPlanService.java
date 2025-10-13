package org.com.code.im.service.learning;

import java.util.List;
import java.util.Map;

public interface LearningPlanService {

    Map<String, Object> createPlan(Long userId, String title, String goal);

    List<Map<String, Object>> getPlansByUserId(Long userId);

    Map<String, Object> getPlanDetails(Long planId, Long userId);

    String updatePlan(Long planId, Long userId, String title, String goal, String status);

    boolean deletePlan(Long planId, Long userId);

    // Reporting methods
    Map<String, Object> getUserLearningSummary(Long userId);

    Map<String, Object> getPlanReport(Long planId, Long userId);
} 