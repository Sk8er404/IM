package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.com.code.im.pojo.UserLearningProgress;
import org.com.code.im.pojo.enums.ContentType;

import java.util.List;

@Mapper
public interface UserLearningProgressMapper {

    /**
     * 插入新的学习进度记录
     * @param progress 学习进度对象
     * @return 影响行数
     */
    int insertUserLearningProgress(UserLearningProgress progress);

    /**
     * 更新已有的学习进度记录 (主要更新lastAccessTimestamp, durationViewedSeconds, completionStatus)
     * @param progress 学习进度对象，必须包含id
     * @return 影响行数
     */
    int updateUserLearningProgress(UserLearningProgress progress);

    /**
     * 根据用户ID、内容ID和内容类型查找唯一的学习进度记录
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param contentType 内容类型 (使用枚举)
     * @return 学习进度对象，如果不存在则返回null
     */
    UserLearningProgress findByUserIdAndContentIdAndContentType(
        @Param("userId") Long userId,
        @Param("contentId") Long contentId,
        @Param("contentType") ContentType contentType
    );

    /**
     * 根据用户ID查询其所有的学习进度记录，按最近访问时间降序排序
     * @param userId 用户ID
     * @return 学习进度记录列表
     */
    List<UserLearningProgress> findByUserIdOrderByLastAccessTimestampDesc(@Param("userId") Long userId);

} 