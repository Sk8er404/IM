package org.com.code.im.service.post.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.exception.ResourceNotFoundException;
import org.com.code.im.mapper.PostCommentMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.pojo.PostComment;
import org.com.code.im.pojo.query.PostCommentPageQuery;
import org.com.code.im.pojo.dto.PostCommentPageResponse;
import org.com.code.im.service.post.PostCommentService;
import org.com.code.im.service.UpdateLatestLikeService;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostCommentImpl implements PostCommentService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    @Qualifier("PostComments")
    private UpdateLatestLikeService updateLatestLikeService;

    @Autowired
    private PostCommentMapper postCommentMapper;


    @Override
    @Transactional
    public Map addComment(PostComment addedComment) {
        try {
            addedComment.setId(SnowflakeIdUtil.postCommentIdWorker.nextId());
            Map<String, Object> map = new HashMap<>();
            map.put("id", addedComment.getId());
            map.put("postId", addedComment.getPostId());
            map.put("userId", addedComment.getUserId());
            String userName=userMapper.selectUserNameById(addedComment.getUserId());
            map.put("userName", userName);
            map.put("parentId", addedComment.getParentId());
            map.put("replyTo", addedComment.getReplyTo());
            map.put("content", addedComment.getContent());

            postCommentMapper.saveComment(map);
            postCommentMapper.increasePostCommentCount(addedComment.getPostId());

            if(addedComment.getParentId()!=null){
                postCommentMapper.increaseCommentCount(addedComment.getParentId());
            }
            return map;
        }catch (Exception e){
            throw new RuntimeException("评论失败");
        }
    }

    @Override
    public PostComment getCommentById(Long commentId) {
        try{
            PostComment comment = postCommentMapper.findCommentById(commentId);
            if (comment == null) {
                throw new ResourceNotFoundException("评论不存在");
            }
            return updateLatestLikeService.updateObjectLikeCount(comment);
        }catch(Exception e){
            throw new DatabaseException("获取评论失败");
        }
    }

    @Override
    @Transactional
    public void updateComment(PostComment existingComment) {
        try {
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("id", existingComment.getId());
            updateMap.put("content", existingComment.getContent());
            updateMap.put("userId", existingComment.getUserId());

            postCommentMapper.updateComment(updateMap);
        }catch (Exception e){
            throw new RuntimeException("更新评论失败");
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        try {
            PostComment comment = postCommentMapper.findCommentById(commentId);
            if (comment == null) {
                return;
            }
            
            int mainDeleted = postCommentMapper.deleteCommentByIdAndUserId(commentId, userId);
            
            if (mainDeleted > 0) {
                // 如果删除的是主评论（parentId为null）
                if (comment.getParentId() == null) {
                    // 删除该主评论下的所有回复
                    if (comment.getRepliesCount() != null && comment.getRepliesCount() > 0) {
                        postCommentMapper.deleteRepliesByParentId(commentId);
                        // 减少帖子评论总数（主评论 + 所有回复）
                        postCommentMapper.decreasePostCommentCount(comment.getRepliesCount(), comment.getPostId());
                    } else {
                        // 只有主评论，减少1个评论数
                        postCommentMapper.decreasePostCommentCount(0L, comment.getPostId());
                    }
                } else {
                    // 如果删除的是回复评论
                    // 减少父评论的回复计数
                    postCommentMapper.decreaseCommentCount(comment.getParentId());
                    // 减少帖子评论总数（只减1）
                    postCommentMapper.decreasePostCommentCount(0L, comment.getPostId());
                }
            }

        } catch (Exception e) {
            throw new DatabaseException("删除评论失败");
        }
    }
    
    // 常量定义
    private static final int PAGE_SIZE = 10;
    
    @Override
    public PostCommentPageResponse getCommentsByPostIdWithCursor(PostCommentPageQuery commentPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某个帖子评论列表的当前页数最小的id小于
             * 帖子评论在post_comments表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果一个帖子终于有了自己的第一个评论，那帖子id和评论会被插入到这个post_comments表格中，
             * 每次post_comments表格的插入autoIncreasementId+1，
             * 如果这个帖子对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个帖子的评论列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该帖子评论最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from post_comments
             *         where postId = #{postId} and parentId IS NULL
             */

            //如果selectPostCommentMinAutoIncrementId()返回值为null，证明该帖子还没有评论，直接返回空
            Long postCommentMinAutoIncrementId = postCommentMapper.selectPostCommentMinAutoIncrementId(commentPageQuery.getPostId(), null);
            if(postCommentMinAutoIncrementId == null)
                return null;

            if(postCommentMinAutoIncrementId > commentPageQuery.getCurPageMinId())
                commentPageQuery.setCurPageMinId(postCommentMinAutoIncrementId);
            
            List<PostComment> commentList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("postId", commentPageQuery.getPostId());
            
            if(commentPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", commentPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = postCommentMapper.queryCommentsByPostIdAndNavigateToNextPageByIdRange(map);
            }else if(commentPageQuery.getNextPage() < 0){
                map.put("curPageMinId", commentPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = postCommentMapper.queryCommentsByPostIdAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(commentList.isEmpty()) {
                return null;
            }
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = commentList.get(0).getAutoIncreasementId();
            long curPageMaxId = commentList.get(commentList.size()-1).getAutoIncreasementId();
            return new PostCommentPageResponse(updateLatestLikeService.updateObjectLikeCountList(commentList)
                    , new PostCommentPageQuery(curPageMaxId, curPageMinId, 0, commentPageQuery.getPostId(), null));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询帖子评论列表失败");
        }
    }
    
    @Override
    public PostCommentPageResponse getRepliesByParentIdWithCursor(PostCommentPageQuery commentPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某个评论回复列表的当前页数最小的id小于
             * 评论回复在post_comments表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果一个评论终于有了自己的第一个回复，那评论id和回复会被插入到这个post_comments表格中，
             * 每次post_comments表格的插入autoIncreasementId+1，
             * 如果这个评论对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个评论的回复列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该评论回复最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from post_comments
             *         where postId = #{postId} and parentId = #{parentId}
             */

            //如果selectPostCommentMinAutoIncrementId()返回值为null，证明该评论还没有回复，直接返回空
            Long postCommentMinAutoIncrementId = postCommentMapper.selectPostCommentMinAutoIncrementId(commentPageQuery.getPostId(), commentPageQuery.getParentId());
            if(postCommentMinAutoIncrementId == null)
                return null;

            if(postCommentMinAutoIncrementId > commentPageQuery.getCurPageMinId())
                commentPageQuery.setCurPageMinId(postCommentMinAutoIncrementId);
            
            List<PostComment> commentList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("postId", commentPageQuery.getPostId());
            map.put("parentId", commentPageQuery.getParentId());
            
            if(commentPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", commentPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = postCommentMapper.queryRepliesByParentIdAndNavigateToNextPageByIdRange(map);
            }else if(commentPageQuery.getNextPage() < 0){
                map.put("curPageMinId", commentPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = postCommentMapper.queryRepliesByParentIdAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(commentList.isEmpty()) {
                return null;
            }
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = commentList.get(0).getAutoIncreasementId();
            long curPageMaxId = commentList.get(commentList.size()-1).getAutoIncreasementId();
            return new PostCommentPageResponse(updateLatestLikeService.updateObjectLikeCountList(commentList)
                    , new PostCommentPageQuery(curPageMaxId, curPageMinId, 0, commentPageQuery.getPostId(), commentPageQuery.getParentId()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询评论回复列表失败");
        }
    }
}
