package org.com.code.im.service.learning.impl;

import lombok.extern.slf4j.Slf4j;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.LearningTaskMapper;
import org.com.code.im.pojo.LearningPlan;
import org.com.code.im.pojo.LearningTask;
import org.com.code.im.mapper.LearningPlanMapper;
import org.com.code.im.rocketMq.producer.MsgProducer;
import org.com.code.im.service.learning.LearningTaskService;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LearningTaskImpl implements LearningTaskService {

    @Autowired
    private LearningTaskMapper taskMapper;

    @Autowired
    private LearningPlanMapper planMapper;

    @Autowired
    private MsgProducer msgProducer;

     private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD
     private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME; // HH:MM:SS or HH:MM

    @Override
    @Transactional
    public Map<String, Object> createTask(LearningTask task) {
         try {
             LearningPlan plan = planMapper.findByIdAndUserId(task.getPlanId(), task.getUserId());
             if (plan == null)
                 return null;

             task.setId(SnowflakeIdUtil.taskIdWorker.nextId());

             taskMapper.saveTask(task);
             LocalDateTime now = LocalDateTime.now().withNano(0);
             task.setCreatedAt(now);
             task.setUpdatedAt(now);

             return convertTaskToMap(task);
         }catch (Exception e){
             e.printStackTrace();
             throw new DatabaseException("计划创建出错");
         }
    }

    @Override
    public List<Map<String, Object>> getTasksByPlanId(Long planId, Long userId) {
          try {
              LearningPlan plan = planMapper.findByIdAndUserId(planId, userId);
              if (plan == null) {
                  return null;
              }
              return taskMapper.findByPlanIdAndUserId(planId, userId)
                      .stream()
                      .map(this::convertTaskToMap)
                      .collect(Collectors.toList());
          }catch (Exception e){
              e.printStackTrace();
              throw new DatabaseException("获取计划出错");
          }
    }

    @Override
    public List<Map<String, Object>> getAllTasks(Long userId) {
        try {
            return taskMapper.findByUserId(userId)
                    .stream()
                    .map(this::convertTaskToMap)
                    .collect(Collectors.toList());
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("获取所有任务出错");
        }
    }

    @Override
     public Map<String, Object> getTaskDetails(Long taskId, Long userId) {
         try {
             LearningTask task = taskMapper.findByIdAndUserId(taskId, userId);
             return (task != null) ? convertTaskToMap(task) : null;
         }catch (Exception e){
             e.printStackTrace();
             throw new DatabaseException("获取任务细节出错");
         }
     }

    @Override
    @Transactional
    public boolean updateTask(LearningTask task) {
        try {
            // 验证任务是否存在且属于当前用户
            LearningTask existingTask = taskMapper.findByIdAndUserId(task.getId(), task.getUserId());
            if (existingTask == null) {
                return false;
            }
            
            // 验证频率值是否有效
            String frequency = task.getFrequency().toUpperCase();
            if (!List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY").contains(frequency)) {
                return false;
            }
            task.setFrequency(frequency);
            
            // 执行更新
            taskMapper.updateTask(task);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("更新任务出错");
        }
    }

    @Override
    @Transactional
    public boolean deleteTask(Long taskId, Long userId) {
         try {
             LearningTask task = taskMapper.findByIdAndUserId(taskId, userId);
             if (task == null) {
                 return false;
             }
             taskMapper.deleteByUserIdAndId(userId, taskId);
             return true;
         }catch (Exception e){
             e.printStackTrace();
             throw new DatabaseException("删除任务出错");
         }
    }

    @Override
    @Transactional
    public boolean markTaskAsComplete(Long taskId, Long userId) {
        try {
            LearningTask task = taskMapper.findByIdAndUserId(taskId, userId);
            if (task == null) {
                return false;
            }
            if (task.isCompletedToday()) {
                return false;
            }
            task.setCompletedToday(true);
            task.setTotalCompletions(task.getTotalCompletions() + 1);
            taskMapper.updateTask(task);
            return true;
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("任务完成出错");
        }
    }

    @Override
    @Transactional
    public void resetDailyTaskCompletionStatus() {
        try {
            taskMapper.resetAllTasksCompletionStatus();
         }catch (Exception e){
             e.printStackTrace();
             throw new DatabaseException("任务重置出错");
         }
    }



    @Override
    @Transactional(readOnly = true)
    public void triggerReminders() {
        try {
            LocalTime now = LocalTime.now();
            LocalTime startOfMinute = now.withSecond(0).withNano(0);

            List<LearningTask> tasksToRemind = taskMapper.findByReminderEnabledTrueAndReminderTimeBetweenAndIsCompletedTodayFalse(startOfMinute, startOfMinute.plusSeconds(59));

            if(tasksToRemind == null||tasksToRemind.isEmpty())
                return;

            for (LearningTask task : tasksToRemind) {
                if (shouldSendReminderToday(task)) {
                    //把用户发送的消息发送到消息队列中
                    msgProducer.sendReminderMessage(task);
                }
            }
         }catch (Exception e){
             e.printStackTrace();
             throw new DatabaseException("任务提醒出错");
         }
    }

     private boolean shouldSendReminderToday(LearningTask task) {
         String frequency = task.getFrequency();
         LocalDate today = LocalDate.now();

         // 检查任务是否已过期（对于有截止日期的任务）
         if (task.getTargetDueDate() != null && today.isAfter(task.getTargetDueDate())) {
             return false; // 已过期的任务不提醒
         }

         switch (frequency) {
             case "ONCE":
                 // 只在截止日期当天提醒，如果没有截止日期则不提醒
                 return task.getTargetDueDate() != null && today.equals(task.getTargetDueDate());
             case "DAILY":
                 return true;
             case "WEEKLY":
                 if (task.getCreatedAt() != null) {
                     return today.getDayOfWeek() == task.getCreatedAt().toLocalDate().getDayOfWeek();
                 }
                 return false; 
             case "MONTHLY":
                 if (task.getCreatedAt() != null) {
                     int taskDay = task.getCreatedAt().toLocalDate().getDayOfMonth();
                     int todayDay = today.getDayOfMonth();
                     int lastDayOfMonth = today.lengthOfMonth();
                     
                     /**
                      * 假设任务是在1月31号创建的,当到了2月29号的时候,
                      * lastDayOfMonth为29,taskDay为31,todayDay为29
                      * 此时应该返回true,因为任务应该在2月29号提醒
                      */
                     if (taskDay > lastDayOfMonth) {
                         return todayDay == lastDayOfMonth;
                     }
                     /*
                      * 如果任务是在2月29号创建的,
                      * 当到了3月29号的时候,
                      * lastDayOfMonth为31,taskDay为29,todayDay为29
                      * 此时应该返回true,因为任务应该在3月29号提醒
                      */
                     return todayDay == taskDay;
                 }
                 return false; 
             default:
                 return false;
         }
     }

    private Map<String, Object> convertTaskToMap(LearningTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("planId", task.getPlanId());
        map.put("userId", task.getUserId());
        map.put("description", task.getDescription());
        map.put("frequency", task.getFrequency());
        map.put("targetDueDate", task.getTargetDueDate() != null ? task.getTargetDueDate().format(DATE_FORMATTER) : null);
        map.put("reminderEnabled", task.getReminderEnabled());
        map.put("reminderTime", task.getReminderTime() != null ? task.getReminderTime().format(TIME_FORMATTER) : null);
        map.put("isCompletedToday", task.isCompletedToday());
        map.put("totalCompletions", task.getTotalCompletions());
        map.put("createdAt", task.getCreatedAt());
        map.put("updatedAt", task.getUpdatedAt());
        return map;
    }


    @Scheduled(cron = "0 1 0 * * ?")
    public void resetDailyCompletionStatus() {
        try {
            this.resetDailyTaskCompletionStatus();
        } catch (Exception e) {
            throw new DatabaseException("数据库日常任务重置失败");
        }
    }

    /**
     * 任务提醒调度器 - 每分钟第0秒执行检查
     * 
     * 调度机制详解：
     * - 使用 cron = "0 * * * * ?" 确保每分钟的第0秒执行
     * - 避免了 fixedRate 导致的时间漂移问题
     *
     * 使用 fixedRate = 60000 的问题：
     * - 如果服务器在15:00:30启动 → 下次检查在15:01:30 → 用户15:00的提醒延迟到15:01:30才发送
     * - 如果服务器在15:00:59启动 → 下次检查在15:01:59 → 用户15:00的提醒延迟到15:01:59才发送
     * - 导致用户误以为系统有bug或延迟
     * 
     * 使用 cron = "0 * * * * ?"：
     * - 每分钟的第0秒执行：15:00:00, 15:01:00, 15:02:00...
     * - 15:00:00的提醒在15:00:00-15:00:59之间准确发送
     * - 15:01:00的提醒在15:01:00-15:01:59之间准确发送
     */

    @Scheduled(cron = "0 * * * * ?")
    public void checkAndSendReminders() {
        try {
            this.triggerReminders();
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("数据库提醒任务失败");
        }
    }

        
    /**
     * 【核心机制】：
     * 1. 定时调度：每60秒(1分钟)执行一次检查
     * 2. 时间窗口：使用59秒时间窗口匹配提醒时间
     * 3. 精确查询：通过SQL BETWEEN语句匹配时间范围
     * 4. 频率判断：根据ONCE/DAILY/WEEKLY/MONTHLY执行不同逻辑
     * 5. 异步发送：通过RocketMQ消息队列处理提醒消息
     * 
     * 【15:00 DAILY提醒详细流程示例】：
     * 假设当前时间：2025-05-29 15:00:23
     * 用户设置：reminderTime=15:00:00, frequency="DAILY"
     * 
     * Step 1: 定时器触发
     *   @Scheduled(fixedRate = 60000) → 每分钟执行checkAndSendReminders()
     * 
     * Step 2: 计算时间窗口
     *   LocalTime now = LocalTime.now();                    // 15:00:23
     *   LocalTime startOfMinute = now.withSecond(0).withNano(0);  // 15:00:00
     *   LocalTime endOfMinute = startOfMinute.plusSeconds(59);    // 15:00:59
     * 
     * Step 3: 数据库查询
     *   SQL: WHERE reminderTime BETWEEN '15:00:00' AND '15:00:59'
     *   结果: 用户的reminderTime=15:00:00在窗口内，任务被查询出来 ✓
     * 
     * Step 4: 频率判断
     *   shouldSendReminderToday(task) → frequency="DAILY" → return true ✓
     * 
     * Step 5: 异步发送
     *   msgProducer.asyncSendMessage("reminder", "task", task)
     * 
     * Step 6: 消息处理
     *   ReminderConsumer接收 → 检查用户在线状态 → WebSocket推送/离线存储
     */
} 