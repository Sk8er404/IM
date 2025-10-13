package org.com.code.im.service.learning;

import org.com.code.im.pojo.UserLearningProgress;
import org.com.code.im.pojo.enums.ContentType; // 引入枚举

import java.util.List;
import java.util.Map;

public interface UserLearningProgressService {

    /**
     * 记录用户查看内容的行为。
     * 如果是首次查看，则创建新记录；否则，更新最近访问时间。
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param contentType 内容类型
     */
    void recordContentView(Long userId, Long contentId, ContentType contentType);

    /**
     * 为指定用户生成学习报告。
     * @param userId 用户ID
     * @return 包含学习报告数据的Map对象 (例如：总学习项目数，各类型项目数，最近学习列表等)
     *         或一个专门的Report DTO对象。
     */
    Map<String, Object> generateLearningReport(Long userId);

    /**
     * 获取用户的所有学习历史记录
     * @param userId 用户ID
     * @return 学习进度记录列表
     */
    List<UserLearningProgress> getLearningProgressHistory(Long userId);

    /**
     * 更新学习时长。
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param contentType 内容类型
     * @param secondsViewed 本次观看的时长（秒）
     */
    void updateLearningDuration(Long userId, Long contentId, ContentType contentType, int secondsViewed);

    /**
     * 标记内容为已完成。
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param contentType 内容类型
     */
    void markContentAsCompleted(Long userId, Long contentId, ContentType contentType);
} 