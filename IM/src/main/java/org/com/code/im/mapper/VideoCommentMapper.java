package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.VideoComments;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoCommentMapper {
    int saveComment(Map comment);
    VideoComments findCommentById(Long id);
    int updateComment(Map<String, Object> updateMap);
    int deleteCommentByIdAndUserId(Long id,Long userId);
    int deleteRepliesByParentId(Long parentId);
    void increaseCommentCount(long id);
    void decreaseCommentCount(long id);
    void increaseVideoCommentsCount(long id);
    void decreaseVideoCommentsCount(long repliesCount,long id);
    
    /**
     * 获取评论最小autoIncreasementId，用于初始化分页查询
     */
    Long selectCommentMinAutoIncrementId(Long videoId, Long parentId);
    
    /**
     * 评论游标分页 - 向后翻页
     */
    List<VideoComments> queryCommentsAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 评论游标分页 - 向前翻页
     */
    List<VideoComments> queryCommentsAndNavigateToPreviousPageByIdRange(Map map);
}
