package org.com.code.im.service.video.impl;

import org.com.code.im.mapper.UserMapper;
import org.com.code.im.mapper.VideoCommentMapper;
import org.com.code.im.pojo.VideoComments;
import org.com.code.im.pojo.query.CommentPageQuery;
import org.com.code.im.pojo.dto.CommentPageResponse;
import org.com.code.im.service.video.VideoCommentService;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VideoCommentImpl implements VideoCommentService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VideoCommentMapper videoCommentMapper;

    // 分页大小常量
    private static final int PAGE_SIZE = 10;

    @Override
    @Transactional
    public Map addComment(VideoComments addedComment) {
        try {
            addedComment.setId(SnowflakeIdUtil.videoCommentIdWorker.nextId());
            Map<String, Object> map = new HashMap<>();
            map.put("id", addedComment.getId());
            map.put("videoId", addedComment.getVideoId());
            map.put("userId", addedComment.getUserId());
            String userName=userMapper.selectUserNameById(addedComment.getUserId());
            map.put("userName", userName);
            map.put("parentId", addedComment.getParentId());
            map.put("replyTo", addedComment.getReplyTo());
            map.put("content", addedComment.getContent());

            videoCommentMapper.saveComment(map);
            videoCommentMapper.increaseVideoCommentsCount(addedComment.getVideoId());

            if(addedComment.getParentId()!=null){
                videoCommentMapper.increaseCommentCount(addedComment.getParentId());
            }
            return map;
        }catch (Exception e){
            throw new RuntimeException("评论失败");
        }
    }

    @Override
    public VideoComments getCommentById(Long commentId) {
        try {
            return videoCommentMapper.findCommentById(commentId);
        } catch (Exception e) {
            throw new RuntimeException("获取评论失败");
        }
    }

    @Override
    @Transactional
    public void updateComment(VideoComments existingComment) {
        try {
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("id", existingComment.getId());
            updateMap.put("content", existingComment.getContent());
            updateMap.put("userId", existingComment.getUserId());

            videoCommentMapper.updateComment(updateMap);
        }catch (Exception e){
            throw new RuntimeException("更新评论失败");
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        try {
            VideoComments comment = videoCommentMapper.findCommentById(commentId);
            if (comment == null) {
                return;
            }
            
            int mainDeleted = videoCommentMapper.deleteCommentByIdAndUserId(commentId, userId);
            
            if (mainDeleted > 0) {
                if (comment.getParentId() == null) {
                    if (comment.getRepliesCount() != null && comment.getRepliesCount() > 0) {
                        videoCommentMapper.deleteRepliesByParentId(commentId);
                        videoCommentMapper.decreaseVideoCommentsCount(comment.getRepliesCount(), comment.getVideoId());
                    } else {
                        videoCommentMapper.decreaseVideoCommentsCount(0L, comment.getVideoId());
                    }
                } else {
                    videoCommentMapper.decreaseCommentCount(comment.getParentId());
                    videoCommentMapper.decreaseVideoCommentsCount(0L, comment.getVideoId());
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("删除评论失败: " + e.getMessage());
        }
    }

    @Override
    public CommentPageResponse getCommentsByVideoIdWithCursor(CommentPageQuery commentPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某视频评论列表的当前页数最小的id小于
             * 该视频评论在video_comments表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果某个视频终于有了第一个评论，那评论会被插入到这个video_comments表格中，
             * 每次video_comments表格的插入autoIncreasementId+1，
             * 如果这个视频评论对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个视频的评论列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该视频评论最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from video_comments where postId = #{videoId} and parentId IS NULL
             */

            //如果selectCommentMinAutoIncrementId()返回值为null，证明该视频还没有评论，直接返回空
            Long commentMinAutoIncrementId = videoCommentMapper.selectCommentMinAutoIncrementId(commentPageQuery.getVideoId(), null);
            if(commentMinAutoIncrementId == null)
                return null;

            if(commentMinAutoIncrementId > commentPageQuery.getCurPageMinId())
                commentPageQuery.setCurPageMinId(commentMinAutoIncrementId);

            List<VideoComments> commentList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("videoId", commentPageQuery.getVideoId());
            map.put("parentId", null); // 查询顶级评论

            if(commentPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", commentPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = videoCommentMapper.queryCommentsAndNavigateToNextPageByIdRange(map);
            }else if(commentPageQuery.getNextPage() < 0){
                map.put("curPageMinId", commentPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = videoCommentMapper.queryCommentsAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(commentList.isEmpty()) {
                return null;
            }
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = commentList.get(0).getAutoIncreasementId();
            long curPageMaxId = commentList.get(commentList.size()-1).getAutoIncreasementId();
            return new CommentPageResponse(commentList, new CommentPageQuery(curPageMaxId, curPageMinId, 0, commentPageQuery.getVideoId(), null));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("未知数据库错误,查询该视频评论列表失败");
        }
    }

    @Override
    public CommentPageResponse getRepliesByParentIdWithCursor(CommentPageQuery commentPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某评论回复列表的当前页数最小的id小于
             * 该评论回复在video_comments表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果某个评论终于有了第一个回复，那回复会被插入到这个video_comments表格中，
             * 每次video_comments表格的插入autoIncreasementId+1，
             * 如果这个评论回复对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个评论的回复列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该评论回复最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from video_comments where postId = #{videoId} and parentId = #{parentId}
             */

            //如果selectCommentMinAutoIncrementId()返回值为null，证明该评论还没有回复，直接返回空
            Long commentMinAutoIncrementId = videoCommentMapper.selectCommentMinAutoIncrementId(commentPageQuery.getVideoId(), commentPageQuery.getParentId());
            if(commentMinAutoIncrementId == null)
                return null;

            if(commentMinAutoIncrementId > commentPageQuery.getCurPageMinId())
                commentPageQuery.setCurPageMinId(commentMinAutoIncrementId);

            List<VideoComments> commentList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("videoId", commentPageQuery.getVideoId());
            map.put("parentId", commentPageQuery.getParentId()); // 查询指定父评论的回复

            if(commentPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", commentPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = videoCommentMapper.queryCommentsAndNavigateToNextPageByIdRange(map);
            }else if(commentPageQuery.getNextPage() < 0){
                map.put("curPageMinId", commentPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -commentPageQuery.getNextPage() * PAGE_SIZE);
                commentList = videoCommentMapper.queryCommentsAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(commentList.isEmpty()) {
                return null;
            }
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = commentList.get(0).getAutoIncreasementId();
            long curPageMaxId = commentList.get(commentList.size()-1).getAutoIncreasementId();
            return new CommentPageResponse(commentList, new CommentPageQuery(curPageMaxId, curPageMinId, 0, commentPageQuery.getVideoId(), commentPageQuery.getParentId()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("未知数据库错误,查询该评论回复列表失败");
        }
    }

    /**
     * 将LocalDateTime转换为时间戳（秒）
     */
    private long convertTimeToTimestamp(LocalDateTime dateTime) {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}
