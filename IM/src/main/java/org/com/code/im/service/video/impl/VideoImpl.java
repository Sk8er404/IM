package org.com.code.im.service.video.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.exception.ElasticSearchException;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.mapper.VideoMapper;
import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.query.VideoPageQuery;
import org.com.code.im.pojo.dto.VideoPageResponse;
import org.com.code.im.ElasticSearch.Service.KeywordDictService;
import org.com.code.im.service.UpdateLatestLikeService;
import org.com.code.im.service.video.VideoService;
import org.com.code.im.utils.KeywordExtractorUtil;
import org.com.code.im.utils.OSSUtil;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.com.code.im.ElasticSearch.Service.ESVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class VideoImpl implements VideoService {
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    @Qualifier("Videos")
    UpdateLatestLikeService updateLatestLikeService;
    @Autowired
    private OSSUtil ossUtil;
    @Autowired
    @Qualifier("strRedisTemplate")
    private RedisTemplate<String, String> strRedisTemplate;
    @Autowired
    private KeywordDictService keywordDictService;
    @Autowired
    private ESVideoService esVideoService;

    // 分页大小常量
    private static final int PAGE_SIZE = 10;

    @Override
    @Transactional
    public Map insertVideo(Map map) {
        try {
            // 提取视频的 title, description的关键词,  tags,category则直接作为关键词存储,不需要提取

            //存储tags和category的关键词，如果有的话
            List<String> keywords =new ArrayList<>();
            if(map.containsKey("tags"))
                keywords.addAll((List<String>)map.get("tags"));
            if(map.containsKey("category"))
                keywords.add(map.get("category").toString());

            //把新的tags和category这种用户自定义的关键词直接存入redis和KeywordDictService的静态Set<String>词典中
            /**
             * 使用 Redis 的  strRedisTemplate.opsForSet().add()，插入数据会自动去重
             * 注意：Redis不允许添加空数组，需要检查keywords是否为空
             */
            if (!keywords.isEmpty()) {
                strRedisTemplate.opsForSet().add("keywords", keywords.stream().toArray(String[]::new));
            keywordDictService.updateKeywordDict(keywords);
            }

            //如果description为空,则只提取title的关键词
            if(map.containsKey("description"))
                KeywordExtractorUtil.extractWords(map.get("title").toString(), map.get("description").toString());
            else
                KeywordExtractorUtil.extractWords(map.get("title").toString());


            map.put("id", SnowflakeIdUtil.videoIdWorker.nextId());
            String userName=userMapper.selectUserNameById((long)map.get("userId"));
            map.put("userName",userName);
            map.put("createdAt", LocalDateTime.now());
            map.put("updatedAt",  LocalDateTime.now());
            videoMapper.insertVideo(map);

            Map videoData = new HashMap<>();
            videoData.put("id", map.get("id"));
            videoData.put("description",  map.get("description"));
            videoData.put("title",  map.get("title"));
            videoData.put("tags",  map.get("tags"));
            videoData.put("category",  map.get("category"));
            videoData.put("createdAt",  map.get("createdAt"));
            
            // 延迟创建ElasticSearch索引
            esVideoService.createVideoIndexDelayed(videoData);
            
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("视频记录插入失败");
        }
    }

    @Override
    @Transactional
    public int deleteVideo(long id,long userId) {
        try {
            int deleteCount=0;
            String filePath = videoMapper.getUrl(id);
            deleteCount = videoMapper.deleteVideo(id,userId);
            if (deleteCount > 0) {
                videoMapper.deleteAllVideoLikePfOneVideo(id);
                videoMapper.deleteAllCommentOfOneVideo(id);
                ossUtil.deleteFile(filePath);
                
                // 删除ElasticSearch索引
                try {
                    esVideoService.deleteVideoIndex(id);
                } catch (Exception e) {
                    throw new ElasticSearchException("ElasticSearch删除视频索引失败");
                    // 即使ES删除失败，也不影响数据库删除操作
                }
            }
            return deleteCount;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("视频记录删除失败");
        }
    }

    @Override
    @Transactional
    public Map queryVideoDetail(long id) {
        try {
            Videos video =videoMapper.queryVideoDetail(id);
            if(video==null)
                return null;
            videoMapper.increaseViewCount(id);
            updateLatestLikeService.updateObjectLikeCount(video);

            Map map = video.toMap();
            map.put("AuthorAvatarUrl",userMapper.selectAvatarById(video.getUserId()));
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询视频详情失败");
        }
    }

    @Override
    public List<Videos> searchVideoByHybridSearch(String keyWords, int page, int size) {
        try {
            List<Long> ids = esVideoService.searchVideoByHybridSearch(keyWords, page, size);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = videoMapper.selectVideoListByManyIds(ids);
            List<Videos> videoList = objectList.stream().map(obj -> (Videos) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(videoList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("根据关键字查询视频失败");
        }
    }

    @Override
    public List<Videos> searchVideoByTime(String startTime, String endTime, int page, int size) {
        try {
            List<Long> ids = esVideoService.searchVideosByTime(startTime, endTime, page, size);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = videoMapper.selectVideoListByManyIds(ids);
            List<Videos> videoList = objectList.stream().map(obj -> (Videos) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(videoList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("按时间查询视频失败");
        }
    }

    @Override
    public List<Videos> selectSelfVideoWaitToReview(long userId) {
        try {
            List<Videos> videos = videoMapper.selectSelfVideoWaitToReview(userId);
            return updateLatestLikeService.updateObjectLikeCountList(videos);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询等待审核视频失败");
        }
    }

    @Override
    public Map querySelfVideoDetail(Map map) {
        try {
            Videos video =videoMapper.querySelfVideoDetail(map);
            if(video==null)
                return null;
            if(video.getStatus().equals("approved")){
                updateLatestLikeService.updateObjectLikeCount(video);
            }

            Map videoMap = video.toMap();
            videoMap.put("AuthorAvatarUrl",userMapper.selectAvatarById(video.getUserId()));
            videoMap.put("status", video.getStatus());
            videoMap.put("reviewNotes", video.getReviewNotes());
            return videoMap;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询视频详情失败");
        }
    }

    @Override
    public List<Videos> selectSelfApprovedVideo(long userId) {
        try {
            List<Videos> videos = videoMapper.selectSelfApprovedVideo(userId);
            /**
             * 已经被审核通过的视频当然每次查询都要获取最新的点赞数更新
             */
            return updateLatestLikeService.updateObjectLikeCountList(videos);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询已经过审视频失败");
        }
    }

    @Override
    public List<Videos> selectSelfRejectedVideo(long userId) {
        try {
            /**
             * 审核拒绝的视频不需要更新点赞数
             */
            return videoMapper.selectSelfRejectedVideo(userId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询未过审视频失败");
        }
    }

    @Override
    public List<Videos> selectAllVideoWaitToReview() {
        try {
            /**
             * 还在审核中的视频不需要更新点赞数
             */
            return videoMapper.selectAllVideoWaitToReview();
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("查询所有待审核视频失败");
        }
    }

    @Override
    @Transactional
    public void updateVideoReviewStatus(long id, String status,long reviewerId,String reviewNotes) {
        try {
            Map<String,Object> map = new HashMap<>();
            map.put("id",id);
            map.put("status",status);
            map.put("reviewerId",reviewerId);
            map.put("reviewNotes",reviewNotes);
            videoMapper.updateVideoReviewStatus(map);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("更新视频失败");
        }
    }

    @Override
    public VideoPageResponse queryLatestVideosWithCursor(VideoPageQuery videoPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询视频列表的当前页数最小的id小于
             * 视频在videos表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果终于有了第一个审核通过的视频，那视频会被插入到这个videos表格中，
             * 每次videos表格的插入autoIncreasementId+1，
             * 如果审核通过的视频对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询最新视频列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取最新视频最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from videos where status = 'approved'
             */

            //如果selectVideoMinAutoIncrementId()返回值为null，证明还没有审核通过的视频，直接返回空
            Long videoMinAutoIncrementId = videoMapper.selectVideoMinAutoIncrementId();
            if(videoMinAutoIncrementId == null)
                return null;

            if(videoMinAutoIncrementId > videoPageQuery.getCurPageMinId())
                videoPageQuery.setCurPageMinId(videoMinAutoIncrementId);

            List<Videos> videoList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);

            if(videoPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", videoPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", videoPageQuery.getNextPage() * PAGE_SIZE);
                videoList = videoMapper.queryLatestVideosAndNavigateToNextPageByIdRange(map);
            }else if(videoPageQuery.getNextPage() < 0){
                map.put("curPageMinId", videoPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -videoPageQuery.getNextPage() * PAGE_SIZE);
                videoList = videoMapper.queryLatestVideosAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(videoList.isEmpty()) {
                return null;
            }
            
            // 更新点赞数
            videoList = updateLatestLikeService.updateObjectLikeCountList(videoList);
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = videoList.get(0).getAutoIncreasementId();
            long curPageMaxId = videoList.get(videoList.size()-1).getAutoIncreasementId();
            return new VideoPageResponse(videoList, new VideoPageQuery(curPageMaxId, curPageMinId, 0, "latest"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询最新视频列表失败");
        }
    }

    @Override
    public VideoPageResponse queryMostViewedVideosWithCursor(VideoPageQuery videoPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询热门视频列表的当前页数最小的id小于
             * 热门视频在videos表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果终于有了第一个审核通过的视频，那视频会被插入到这个videos表格中，
             * 每次videos表格的插入autoIncreasementId+1，
             * 如果审核通过的视频对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询热门视频列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取热门视频最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from videos where status = 'approved'
             */

            //如果selectVideoMinAutoIncrementId()返回值为null，证明还没有审核通过的视频，直接返回空
            Long videoMinAutoIncrementId = videoMapper.selectVideoMinAutoIncrementId();
            if(videoMinAutoIncrementId == null)
                return null;

            if(videoMinAutoIncrementId > videoPageQuery.getCurPageMinId())
                videoPageQuery.setCurPageMinId(videoMinAutoIncrementId);

            List<Videos> videoList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);

            if(videoPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", videoPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", videoPageQuery.getNextPage() * PAGE_SIZE);
                videoList = videoMapper.queryMostViewedVideosAndNavigateToNextPageByIdRange(map);
            }else if(videoPageQuery.getNextPage() < 0){
                map.put("curPageMinId", videoPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -videoPageQuery.getNextPage() * PAGE_SIZE);
                videoList = videoMapper.queryMostViewedVideosAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(videoList.isEmpty()) {
                return null;
            }
            
            // 更新点赞数
            videoList = updateLatestLikeService.updateObjectLikeCountList(videoList);
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = videoList.get(0).getAutoIncreasementId();
            long curPageMaxId = videoList.get(videoList.size()-1).getAutoIncreasementId();
            return new VideoPageResponse(videoList, new VideoPageQuery(curPageMaxId, curPageMinId, 0, "mostViewed"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询热门视频列表失败");
        }
    }

    /**
     * 将时间戳转换为秒（用于最新视频查询的时间比较）
     */
    private long convertTimestampToSeconds(long timestamp) {
        return timestamp / 1000;
    }

    /**
     * 将LocalDateTime转换为时间戳（秒）
     */
    private long convertTimeToTimestamp(LocalDateTime dateTime) {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}
