package org.com.code.im.pojo;

import org.com.code.im.pojo.enums.CompletionStatus;
import org.com.code.im.pojo.enums.ContentType;

import java.sql.Timestamp; // 使用 java.sql.Timestamp 以便MyBatis更好地处理JDBC类型

/**
 * 用户学习进度实体类
 */
public class UserLearningProgress {

    private Long id;
    private Long userId;
    private Long contentId;
    private ContentType contentType; // 使用枚举类型
    private Timestamp firstAccessTimestamp;
    private Timestamp lastAccessTimestamp;
    private Integer durationViewedSeconds;
    private CompletionStatus completionStatus; // 使用枚举类型

    // 构造函数 (空构造函数为MyBatis或框架实例化所需)
    public UserLearningProgress() {
    }

    // 可根据需要添加其他构造函数, 例如初始化核心字段的
    public UserLearningProgress(Long userId, Long contentId, ContentType contentType) {
        this.userId = userId;
        this.contentId = contentId;
        this.contentType = contentType;
        // 可以在Service层设置默认时间戳和状态
    }

    // Getter 和 Setter 方法 (必需)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public Timestamp getFirstAccessTimestamp() {
        return firstAccessTimestamp;
    }

    public void setFirstAccessTimestamp(Timestamp firstAccessTimestamp) {
        this.firstAccessTimestamp = firstAccessTimestamp;
    }

    public Timestamp getLastAccessTimestamp() {
        return lastAccessTimestamp;
    }

    public void setLastAccessTimestamp(Timestamp lastAccessTimestamp) {
        this.lastAccessTimestamp = lastAccessTimestamp;
    }

    public Integer getDurationViewedSeconds() {
        return durationViewedSeconds;
    }

    public void setDurationViewedSeconds(Integer durationViewedSeconds) {
        this.durationViewedSeconds = durationViewedSeconds;
    }

    public CompletionStatus getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(CompletionStatus completionStatus) {
        this.completionStatus = completionStatus;
    }

    @Override
    public String toString() {
        return "UserLearningProgress{" +
                "id=" + id +
                ", userId=" + userId +
                ", contentId=" + contentId +
                ", ContentType=" + contentType +
                ", firstAccessTimestamp=" + firstAccessTimestamp +
                ", lastAccessTimestamp=" + lastAccessTimestamp +
                ", durationViewedSeconds=" + durationViewedSeconds +
                ", completionStatus=" + completionStatus +
                '}';
    }
} 