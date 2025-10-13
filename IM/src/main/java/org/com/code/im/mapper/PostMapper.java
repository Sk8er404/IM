package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.com.code.im.pojo.Posts;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper {

    void savePost(Posts post);

    Posts findPostById(Long id);

    List<Posts> findPostsByUserId(Long userId);

    List<Posts> findPostsByType(Posts.PostType type,int page,int size);

    int updatePost(Map<String, Object> updateMap);

    int incrementViewCount(Long id);

    List<Object> selectPostListByManyIds(List<Long> ids);

    List<Posts> queryLatestPosts(int page, int size);

    List<Posts> queryMostViewedPosts(int page, int size);

    int deletePostByIdAndUserId(Long id,Long userId);

    int deleteAllPostLikePfOnePost(Long postId);

    int deleteAllCommentOfOnePost(long postId);

    int deleteAllCommentLikeOfOnePost(long postId);

    /**
     * 获取帖子最小autoIncreasementId，用于初始化分页查询
     */
    Long selectPostMinAutoIncrementId(Long userId, Posts.PostType postType);


    /**
     * 帖子游标分页 - 向后翻页（按类型查询）
     */
    List<Posts> queryPostsByTypeAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 帖子游标分页 - 向前翻页（按类型查询）
     */
    List<Posts> queryPostsByTypeAndNavigateToPreviousPageByIdRange(Map map);
    
    /**
     * 帖子游标分页 - 向后翻页（按用户查询）
     */
    List<Posts> queryPostsByUserIdAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 帖子游标分页 - 向前翻页（按用户查询）
     */
    List<Posts> queryPostsByUserIdAndNavigateToPreviousPageByIdRange(Map map);
    
    /**
     * 最新帖子游标分页 - 向后翻页
     */
    List<Posts> queryLatestPostsAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 最新帖子游标分页 - 向前翻页
     */
    List<Posts> queryLatestPostsAndNavigateToPreviousPageByIdRange(Map map);
    
    /**
     * 热门帖子游标分页 - 向后翻页
     */
    List<Posts> queryMostViewedPostsAndNavigateToNextPageByIdRange(Map map);
    
    /**
     * 热门帖子游标分页 - 向前翻页
     */
    List<Posts> queryMostViewedPostsAndNavigateToPreviousPageByIdRange(Map map);
} 