package org.com.code.im.rocketMq.consumer;

import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.com.code.im.netty.nettyHandler.ChannelCrud;
import org.com.code.im.pojo.LearningTask;
import org.com.code.im.responseHandler.ResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.TimeUnit;

@Component
@RocketMQMessageListener(topic = "${rocketmq.topics.topic2}",
        consumerGroup = "${rocketmq.consumer.group5}",
        selectorExpression = "${rocketmq.tags.tag5}",
        messageModel = MessageModel.CLUSTERING)
public class ReminderConsumer implements RocketMQListener<String> {

    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;

    @Qualifier("strRedisTemplate")
    @Autowired
    private RedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String messages) {
        LearningTask task = JSONObject.parseObject(messages, LearningTask.class);

        Long userId=(Long)redisTemplate.opsForHash().get("online_user",String.valueOf(task.getUserId()));
        /**
         * 消息提醒的时候用户不在线,则把消息作为离线消息存储起来.
         */
        if(userId==null){
            String key = "task_reminder_" + task.getUserId();
            stringRedisTemplate.opsForSet().add(key, messages);

            // 根据任务频率设置不同的过期时间
            long expireSeconds = calculateExpireTime(task);
            stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }else{
            /**
             * 用户在线,则发送消息给用户,发送后的消息不存储到数据库中
             */
            ResponseHandler responseHandler = new ResponseHandler(ResponseHandler.SUCCESS, "任务提醒消息",task);
            ChannelCrud.sendMessage(task.getUserId(),JSONObject.toJSONString(responseHandler));
        }
    }

    /**
     * 根据任务频率计算离线消息的过期时间
     *
     * 设计原则：
     * 1. ONCE: 一次性任务，用户错过了仍希望看到，给3天缓冲期，让用户有充足时间看到提醒
     * 2. DAILY: 每日任务，过期时间到下一次提醒前
     * 3. WEEKLY: 每周任务，过期时间到下一周同一天
     * 4. MONTHLY: 每月任务，过期时间到下个月同一天（考虑大小月）
     *
     * @param task 学习任务
     * @return 过期时间（秒）
     */
    private long calculateExpireTime(LearningTask task) {
        String frequency = task.getFrequency();

        switch (frequency) {
            case "ONCE":
                // 一次性任务：设置3天过期时间
                // 用户错过了这个重要提醒，给3天缓冲期让用户看到
                return 3 * 24 * 60 * 60L; // 3天 = 259200秒

            case "DAILY":
                // 每日任务：设置到下一天同一时间
                // 避免用户看到昨天的提醒，因为今天还会有新的提醒
                return 24 * 60 * 60L; // 24小时 = 86400秒

            case "WEEKLY":
                // 每周任务：设置到下一周同一天同一时间
                // 例如：周一9点的提醒，过期时间到下周一9点前
                return 7 * 24 * 60 * 60L; // 7天 = 604800秒

            case "MONTHLY":
                // 每月任务：设置到下个月同一天（考虑大小月份）
                return calculateMonthlyExpireTime(task);

            default:
                // 未知频率，默认1天
                return 24 * 60 * 60L;
        }
    }

    /**
     * 计算月度任务的过期时间（处理大小月份问题）
     *
     * 复杂度说明：
     * - 需要考虑2月28/29天的问题
     * - 需要考虑大月31天，小月30天的问题
     * - 需要考虑闰年的问题
     *
     * 示例：
     * - 1月31日的提醒 → 2月28日(平年)/29日(闰年)过期
     * - 1月15日的提醒 → 2月15日过期
     * - 2月29日的提醒 → 3月29日过期（闰年特殊情况）
     *
     * @param task 学习任务
     * @return 过期时间（秒）
     */
    private long calculateMonthlyExpireTime(LearningTask task) {
        LocalDate today = LocalDate.now();
        LocalDate nextMonth;

        // 获取任务创建的日期
        int taskDay = task.getCreatedAt().toLocalDate().getDayOfMonth();

        // 计算下个月的年月
        YearMonth currentYearMonth = YearMonth.from(today);
        YearMonth nextYearMonth = currentYearMonth.plusMonths(1);

        // 获取下个月的天数
        int nextMonthLength = nextYearMonth.lengthOfMonth();

        // 处理月末日期问题
        if (taskDay > nextMonthLength) {
            // 如果任务日期超过下个月的天数，设置为下个月的最后一天
            nextMonth = nextYearMonth.atEndOfMonth();
        } else {
            // 正常情况，设置为下个月的对应日期
            nextMonth = nextYearMonth.atDay(taskDay);
        }

        // 计算从现在到下个月对应日期的秒数
        long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, nextMonth);
        return daysDifference * 24 * 60 * 60L;
    }
}
