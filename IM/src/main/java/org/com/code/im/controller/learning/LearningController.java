package org.com.code.im.controller.learning;

import org.com.code.im.pojo.LearningPlan;
import org.com.code.im.pojo.LearningTask;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.learning.LearningPlanService;
import org.com.code.im.service.learning.LearningTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
public class LearningController {

    @Autowired
    private LearningPlanService learningPlanService;

    @Autowired
    private LearningTaskService learningTaskService;

    private long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /**
     *
     * 用户输入参数为 json ,"title" 和 "goal"都不能为空
     * {
     *     "title": "学习计划标题", //不能为空
     *     "goal": "学习计划目标" //不能为空
     * }
     * @return
     */
    @PostMapping("/api/learning/createPlan")
    public ResponseHandler createPlan(@RequestBody LearningPlan plan) {
        long userId = getCurrentUserId();

        String title = plan.getTitle();
        String goal = plan.getGoal();

        if(title == null || goal == null||title.trim().isEmpty() || goal.trim().isEmpty())
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "标题或目标不能为空");

        Map<String, Object> planDetails = learningPlanService.createPlan(userId, plan.getTitle(), plan.getGoal());
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习计划创建成功", planDetails);
    }

    @GetMapping("/api/learning/getAllPlans")
    public ResponseHandler getPlans() {
        long userId = getCurrentUserId();
        List<Map<String, Object>> plans = learningPlanService.getPlansByUserId(userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", plans);
    }

    @GetMapping("/api/learning/planInDetail/{planId}")
    public ResponseHandler getPlanDetails(@PathVariable("planId") long planId) {
        long userId = getCurrentUserId();
        Map<String, Object> planDetails = learningPlanService.getPlanDetails(planId, userId);
        if (planDetails == null) {
            return new ResponseHandler(ResponseHandler.NOT_FOUND, "未找到学习计划或无权限");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", planDetails);
    }

    /**
     *用户输入参数为 json
     * {
     *      "planId": 1,
     *      "title": "学习计划标题",// 可选
     *      "goal": "学习计划目标",// 可选
     *      "status": "学习计划状态"// 可选
     * }
     */
    @PutMapping("/api/learning/updatePlan")
    public ResponseHandler updatePlan(@RequestBody LearningPlan plan) {
        long userId = getCurrentUserId();
        if  (plan.getId() == null||  plan.getId() <= 0) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "计划ID不能为空或者不合法");
        }
        String result = learningPlanService.updatePlan(plan.getId(), userId, plan.getTitle(), plan.getGoal(), plan.getStatus());
        if (!result.isEmpty()) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "计划更新失败"+result);
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习计划更新成功");
    }

    @DeleteMapping("/api/learning/deletePlan/{planId}")
    public ResponseHandler deletePlan(@PathVariable("planId") long planId) {
        long userId = getCurrentUserId();
        boolean deleted = learningPlanService.deletePlan(planId, userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习计划删除成功");
    }

    // --- Learning Task ---

    /**
     * 创建学习任务 - 全部参数通过JSON传递
     * 请求体示例:
     * {
     *     "planId":  1,                    //  必填
     *     "description": "学习Spring Boot", // 必填
     *     "frequency": "DAILY",            // 必填: "ONCE", "DAILY", "WEEKLY", "MONTHLY"
     *     "targetDueDate": "2025-06-01",   // 可选: YYYY-MM-DD格式
     *     "reminderEnabled": true,         // 可选: 默认false
     *     "reminderTime": "09:00:00"       // 可选: HH:MM:SS格式，如果reminderEnabled为true则必填
     * }
     */
    @PostMapping("/api/learning/plans/createTask")
    public ResponseHandler createTask(@RequestBody LearningTask task) {

        long userId = getCurrentUserId();
        
        // 验证必填字段
        if (task.getDescription() == null || task.getDescription().trim().isEmpty()) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "任务描述不能为空");
        }
        if (task.getFrequency() == null || task.getFrequency().trim().isEmpty()) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "任务频率不能为空");
        }
        task.setFrequency(task.getFrequency().toUpperCase());
        if (!List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY").contains(task.getFrequency())) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "任务频率无效");
        }
        /**
         * 如果前端输错ReminderTime格式，
         * 当JSON中的时间格式与 @JsonFormat(pattern = "HH:mm:ss") 不匹配时：
         * 第1步：Jackson反序列化阶段
         * 
         * @JsonFormat(pattern = "HH:mm:ss")
         * private LocalTime reminderTime;
         * 
         * 如果格式不匹配：抛出 JsonParseException 或 InvalidFormatException
         * 请求直接失败，返回 400 Bad Request
         * 不会进入Controller方法
         *
         * 错误响应：
         * {
         *     "timestamp": "2025-05-27T17:30:00.000+00:00",
         *     "status": 400,
         *     "error": "Bad Request",
         *     "message": "JSON parse error: Cannot deserialize value of type `java.time.LocalTime` from String \"09:30\": Failed to deserialize java.time.LocalTime: (java.time.format.DateTimeParseException) Text '09:30' could not be parsed at index 5",
         *     "path": "/api/learning/plans/createTask"
         * }
         * 
         */
        if(task.getReminderEnabled() && task.getReminderTime() == null){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "提醒时间不能为空");
        }
        if(task.getReminderTime()!= null)
            task.setReminderEnabled(true);

        task.setUserId(userId);

        Map<String, Object> taskDetails = learningTaskService.createTask(task);
        if (taskDetails == null) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "创建任务失败，不存在该任务对应的计划");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习任务创建成功", taskDetails);
    }

    @GetMapping("/api/learning/plans/{planId}/getAllTasksOfAPlan")
    public ResponseHandler getTasksForPlan(@PathVariable("planId") long planId) {
        long userId = getCurrentUserId();
        List<Map<String, Object>> tasks = learningTaskService.getTasksByPlanId(planId, userId);
         if (tasks == null) {
             return new ResponseHandler(ResponseHandler.NOT_FOUND, "未找到计划或无权限");
         }
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", tasks);
    }

     @GetMapping("/api/learning/taskInDetail/{taskId}")
     public ResponseHandler getTaskDetails(@PathVariable("taskId") long taskId) {
         long userId = getCurrentUserId();
         Map<String, Object> taskDetails = learningTaskService.getTaskDetails(taskId, userId);
         if (taskDetails == null) {
             return new ResponseHandler(ResponseHandler.NOT_FOUND, "未找到任务或无权限");
         }
         return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", taskDetails);
     }


    /**
     * 更新学习任务 - 全部参数通过JSON传递
     * 请求体示例（只传递需要更新的字段）:
     * {
     *     "id": 123456789,                    // 必填: 任务ID
     *     "description": "更新后的任务描述",    // 可选
     *     "frequency": "WEEKLY",              // 可选: "ONCE", "DAILY", "WEEKLY", "MONTHLY"  
     *     "targetDueDate": "2025-07-01",      // 可选: YYYY-MM-DD格式
     *     "reminderEnabled": false,           // 可选
     *     "reminderTime": "10:30:00",          // 可选: HH:MM:SS格式
     *     "isCompletedToday": true           // 可选
     * }
     */
    @PutMapping("/api/learning/updateTask")
    public ResponseHandler updateTask(@RequestBody LearningTask task) {
        long userId = getCurrentUserId();
        
        // 验证必填字段
        if (task.getId() == null) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "任务ID不能为空");
        }
        
        // 验证频率值（如果提供了的话）
        if (task.getFrequency() != null && !task.getFrequency().trim().isEmpty()) {
            task.setFrequency(task.getFrequency().toUpperCase());
            if (!List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY").contains(task.getFrequency())) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "任务频率无效");
            }
        }
        
        // 验证提醒时间逻辑
        if (task.getReminderEnabled() && task.getReminderTime() == null) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "启用提醒时必须设置提醒时间");
        }
        if (!task.getReminderEnabled()&&task.getReminderTime() != null) {
            task.setReminderEnabled(true);
        }
        
        task.setUserId(userId);
        boolean updated = learningTaskService.updateTask(task);
        if (!updated) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "更新失败或未找到任务");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习任务更新成功");
    }

    @DeleteMapping("/api/learning/deleteTask/{taskId}")
    public ResponseHandler deleteTask(@PathVariable("taskId") long taskId) {
        long userId = getCurrentUserId();
        boolean deleted = learningTaskService.deleteTask(taskId, userId);
        if (!deleted) {
             return new ResponseHandler(ResponseHandler.SERVER_ERROR, "删除失败或未找到任务");
         }
        return new ResponseHandler(ResponseHandler.SUCCESS, "学习任务删除成功");
    }

    @PostMapping("/api/learning/completeTask/{taskId}")
    public ResponseHandler completeTask(@PathVariable("taskId") long taskId) {
        long userId = getCurrentUserId();
        boolean completed = learningTaskService.markTaskAsComplete(taskId, userId);
        if (!completed) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "标记完成失败，任务可能已完成或不存在");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS, "任务已标记为完成");
    }


    @GetMapping("/api/learning/reports/summary")
    public ResponseHandler getLearningSummary() {
        long userId = getCurrentUserId();
        Map<String, Object> summary = learningPlanService.getUserLearningSummary(userId); // Or a dedicated ReportService
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", summary);
    }

     @GetMapping("/api/learning/reports/plan/{planId}")
     public ResponseHandler getPlanReport(@PathVariable("planId") long planId) {
         long userId = getCurrentUserId();
         Map<String, Object> report = learningPlanService.getPlanReport(planId, userId);
         if (report == null) {
             return new ResponseHandler(ResponseHandler.NOT_FOUND, "未找到计划或无权限");
         }
         return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", report);
     }

     @GetMapping("/api/learning/getAllPlansAndAllTasks")
     public ResponseHandler getAllPlansAndAllTasks() {
         long userId = getCurrentUserId();
         List<Map<String,Object>> planList = learningPlanService.getPlansByUserId(userId);
         List<Map<String,Object>> taskList = learningTaskService.getAllTasks(userId);

          return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功",
                 Map.of("plans", planList, "tasks", taskList));
     }
}