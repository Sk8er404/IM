package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.PostComment;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostCommentMapper {

    int saveComment(Map comment);
    PostComment findCommentById(Long id);
    
    // 传统分页方法（保持兼容）
    List<PostComment> findCommentsByPostId(Long postId, int page, int size);
    List<PostComment> findRepliesByParentId(Long postId,Long parentId,int page, int size);
    
    int updateComment(Map<String, Object> updateMap);
    int deleteCommentByIdAndUserId(Long id,Long userId);
    int deleteRepliesByParentId(Long parentId);
    void increaseCommentCount(long id);
    void decreaseCommentCount(long id);
    void increasePostCommentCount(long id);
    void decreasePostCommentCount(long repliesCount,long id);
    
    /**
     * 获取帖子评论最小autoIncrementId，用于初始化分页查询
     */
    Long selectPostCommentMinAutoIncrementId(Long postId, Long parentId);
    
    /**
     * 帖子评论游标分页 - 向后翻页（获取帖子顶级评论）
     */
    List<PostComment> queryCommentsByPostIdAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 帖子评论游标分页 - 向前翻页（获取帖子顶级评论）  
     */
    List<PostComment> queryCommentsByPostIdAndNavigateToPreviousPageByIdRange(Map map);
    
    /**
     * 帖子评论回复游标分页 - 向后翻页（获取评论回复）
     */
    List<PostComment> queryRepliesByParentIdAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 帖子评论回复游标分页 - 向前翻页（获取评论回复）
     */
    List<PostComment> queryRepliesByParentIdAndNavigateToPreviousPageByIdRange(Map map);
} 