package org.com.code.im.service.learning.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.pojo.LearningPlan;
import org.com.code.im.pojo.LearningTask; // Import LearningTask
import org.com.code.im.mapper.LearningPlanMapper;
import org.com.code.im.mapper.LearningTaskMapper;
import org.com.code.im.service.learning.LearningPlanService;
import org.com.code.im.utils.SnowflakeIdUtil; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LearningPlanImpl implements LearningPlanService {

    @Autowired
    private LearningPlanMapper planMapper;

    @Autowired
    private LearningTaskMapper taskMapper;

    @Override
    @Transactional
    public Map<String, Object> createPlan(Long userId, String title, String goal) {
        try {
            LearningPlan plan = new LearningPlan();
            plan.setId(SnowflakeIdUtil.planIdWorker.nextId()); // Assuming a specific worker for plan IDs
            plan.setUserId(userId);
            plan.setTitle(title);
            plan.setGoal(goal);
            plan.setStatus("ACTIVE");

            planMapper.savePlan(plan);
            LocalDateTime now = java.time.LocalDateTime.now().withNano(0);
            plan.setCreatedAt(now);
            plan.setUpdatedAt(now);

            return convertPlanToMap(plan);
        }catch (Exception e){
            throw new DatabaseException("计划创建出错");
        }
    }

    @Override
    public List<Map<String, Object>> getPlansByUserId(Long userId) {
        return planMapper.findByUserId(userId)
                .stream()
                .map(this::convertPlanToMap)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getPlanDetails(Long planId, Long userId) {
        LearningPlan plan = planMapper.findByIdAndUserId(planId, userId);
        return (plan != null) ? convertPlanToMap(plan) : null;
    }

    @Override
    @Transactional
    public String updatePlan(Long planId, Long userId, String title, String goal, String status) {
        try {
            LearningPlan plan = planMapper.findByIdAndUserId(planId, userId);
            if (plan == null) {
                return "计划不存在";
            }
            boolean updated = false;
            String result = "";
            if (title != null && !title.trim().isEmpty()) {
                plan.setTitle(title);
                updated = true;
            }
            if (goal != null&& !goal.trim().isEmpty()) {
                plan.setGoal(goal);
                updated = true;
            }
            if (status != null && !status.trim().isEmpty()) {
                status=status.toUpperCase();
                if(List.of("ACTIVE", "COMPLETED", "CANCELLED").contains(status)){
                    plan.setStatus(status);
                    updated = true;
                }else{
                    result = "计划状态无效";
                }
            }

            if (updated)
                planMapper.updatePlan(plan);
                
            return result;
        }catch (Exception e){
            throw new DatabaseException("计划更新出错");
        }
    }

    @Override
    @Transactional
    public boolean deletePlan(Long id, Long userId) {
        try {
            LearningPlan plan = planMapper.findByIdAndUserId(id, userId);
            if (plan == null) {
                return false;
            }
            taskMapper.deleteByUserIdAndId(userId,id);
            planMapper.deleteByUserIdAndId(userId,id);
            return true;
        }catch (Exception e){
            throw new DatabaseException("计划删除出错");
        }
    }

    @Override
    public Map<String, Object> getUserLearningSummary(Long userId) {
        Map<String, Object> summary = new HashMap<>();
        List<LearningPlan> plans = planMapper.findByUserId(userId);
        List<LearningTask> tasks = taskMapper.findByUserId(userId);

        long totalPlans = plans.size();
        long activePlans = plans.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count();
        long totalTasks = tasks.size();
        long completedToday = tasks.stream().filter(t -> t.isCompletedToday()).count();
        long totalCompletions = tasks.stream().mapToInt(LearningTask::getTotalCompletions).sum();

        summary.put("totalPlans", totalPlans);
        summary.put("activePlans", activePlans);
        summary.put("totalTasks", totalTasks);
        summary.put("tasksCompletedToday", completedToday);
        summary.put("totalCompletionsAllTime", totalCompletions);
        return summary;
    }

     @Override
     public Map<String, Object> getPlanReport(Long planId, Long userId) {
         LearningPlan plan = planMapper.findByIdAndUserId(planId, userId);
         if (plan == null) {
             return null; 
         }

         List<LearningTask> tasks = taskMapper.findByPlanIdAndUserId(planId, userId);
         long totalTasksInPlan = tasks.size();
         long completedTodayInPlan = tasks.stream().filter(t -> t.isCompletedToday()).count();
         long totalCompletionsInPlan = tasks.stream().mapToInt(LearningTask::getTotalCompletions).sum();

         Map<String, Object> report = new HashMap<>();
         report.put("planDetails", convertPlanToMap(plan));
         report.put("totalTasksInPlan", totalTasksInPlan);
         report.put("tasksCompletedTodayInPlan", completedTodayInPlan);
         report.put("totalCompletionsInPlan", totalCompletionsInPlan);
         return report;
     }

    private Map<String, Object> convertPlanToMap(LearningPlan plan) {
        Map<String, Object> map = new HashMap<>();
        if(plan.getId()!=null)
            map.put("id", plan.getId());
        if(plan.getUserId()!=null)
            map.put("userId", plan.getUserId());
        if(plan.getTitle()!=null)
            map.put("title", plan.getTitle());
        if(plan.getGoal()!=null)
            map.put("goal", plan.getGoal());
        if (plan.getStatus()!=null)
            map.put("status", plan.getStatus());
        if(plan.getCreatedAt()!=null)
            map.put("createdAt", plan.getCreatedAt());
        if(plan.getUpdatedAt()!=null)
            map.put("updatedAt", plan.getUpdatedAt());
        return map;
    }
} 