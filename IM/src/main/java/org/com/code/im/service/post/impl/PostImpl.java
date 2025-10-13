package org.com.code.im.service.post.impl;

import org.com.code.im.ElasticSearch.Service.ESPostService;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.PostMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.query.PostPageQuery;
import org.com.code.im.pojo.dto.PostPageResponse;
import org.com.code.im.ElasticSearch.Service.KeywordDictService;
import org.com.code.im.service.post.PostService;
import org.com.code.im.service.UpdateLatestLikeService;
import org.com.code.im.utils.KeywordExtractorUtil;
import org.com.code.im.utils.SnowflakeIdUtil; // Assuming you have this for ID generation
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostImpl implements PostService{

    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    @Qualifier("Posts")
    private UpdateLatestLikeService updateLatestLikeService;
    @Autowired
    @Qualifier("strRedisTemplate")
    private RedisTemplate<String, String> strRedisTemplate;
    @Autowired
    KeywordDictService keywordDictService;
    @Autowired
    ESPostService esPostService;

    // 分页大小常量
    private static final int PAGE_SIZE = 10;

    @Override
    @Transactional
    public Posts createPost(Posts post) {
       try{
           //把新的tags这种用户自定义的关键词直接存入redis和KeywordDictService的静态Set<String>词典中
           /**
            * 使用 Redis 的  strRedisTemplate.opsForSet().add()，插入数据会自动去重
            */
           if(post.getTags()!=null&&post.getTags().size()>0){
               strRedisTemplate.opsForSet().add("keywords",post.getTags().stream().toArray(String[]::new));
               keywordDictService.updateKeywordDict(post.getTags());
           }
           KeywordExtractorUtil.extractWords(post.getTitle(),post.getContent());

           /**
            * 把帖子数据存入数据库
            */
           post.setId(SnowflakeIdUtil.postIdWorker.nextId()); // Example ID generation
           post.setCreatedAt(LocalDateTime.now());
           post.setUpdatedAt(LocalDateTime.now());
           post.setUserName(userMapper.selectUserNameById(post.getUserId()));
           postMapper.savePost(post);

           /**
            * 把帖子数据存放入ElasticSearch中,但是不立即执行,而是延迟执行,
            * 延迟是为了等到分词器提取到帖子信息的关键词之后，还要等待关键词同步到
            * ES搜索引擎中的IK分词器的词典中，然后分词器才能把帖子信息根据关键字分词，
            * 此时才能开始创建ES索引
            */
           Map<String,Object> postData = new HashMap<>();
           postData.put("id",post.getId());
           postData.put("title",post.getTitle());
           postData.put("content",post.getContent());
           postData.put("tags",post.getTags());
           postData.put("createdAt",post.getCreatedAt());
           esPostService.createPostIndexDelayed(postData);

           return post;
       }catch(Exception e){
           e.printStackTrace();
           throw new DatabaseException("创建帖子失败");
       }
    }

    @Override
    public Posts getPostById(Long postId) {
        try{
            postMapper.incrementViewCount(postId);
            Posts post = postMapper.findPostById(postId);

            return updateLatestLikeService.updateObjectLikeCount(post);
        }catch(Exception e){
            throw new DatabaseException("获取帖子失败");
        }
    }

    @Override
    @Transactional
    public void updatePost(Posts existingPost) {
        try{

            KeywordExtractorUtil.extractWords(existingPost.getTitle(),existingPost.getContent());

            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("id", existingPost.getId());
            updateMap.put("userId", existingPost.getUserId());
            updateMap.put("title", existingPost.getTitle());
            updateMap.put("content", existingPost.getContent());
            updateMap.put("tags", existingPost.getTags());
            postMapper.updatePost(updateMap);

            esPostService.updatePostIndex(updateMap);
        }catch(Exception e){
            throw new DatabaseException("更新帖子失败");
        }
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
       try{
           int deleteCount =postMapper.deletePostByIdAndUserId(postId, userId);
           if(deleteCount>0){
               postMapper.deleteAllPostLikePfOnePost(postId);
               postMapper.deleteAllCommentOfOnePost(postId);
               postMapper.deleteAllCommentLikeOfOnePost(postId);

               //删除es中的索引
               esPostService.deletePostIndex(postId);
           }
       }catch(Exception e){
           throw new DatabaseException("删除帖子失败");
       }
    }

    @Override
    public List<Posts> getPostsByHybridSearch(String keyWord, int page, int size) {
        try{
            List<Long> ids=esPostService.searchPostByHybridSearch(keyWord,page,size);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = postMapper.selectPostListByManyIds(ids);
            List<Posts> postList = objectList.stream().map(obj -> (Posts) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(postList);
        }catch(Exception e){
            throw new DatabaseException("获取帖子失败");
        }
    }

    @Override
    public List<Posts> searchPostByTime(String startTime, String endTime, int page, int size) {
        try{
            List<Long> ids = esPostService.searchPostsByTime(startTime,endTime,page,size);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> objectList = postMapper.selectPostListByManyIds(ids);
            List<Posts> postList = objectList.stream().map(obj -> (Posts) obj).toList();
            return updateLatestLikeService.updateObjectLikeCountList(postList);
        }catch(Exception e){
            e.printStackTrace();
            throw new DatabaseException("按时间查询帖子失败");
        }
    }

    @Override
    public PostPageResponse getPostsByTypeWithCursor(PostPageQuery postPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某类型帖子列表的当前页数最小的id小于
             * 该类型帖子在posts表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果某个类型终于有了第一个帖子，那帖子会被插入到这个posts表格中，
             * 每次posts表格的插入autoIncreasementId+1，
             * 如果这个类型帖子对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个类型的帖子列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该类型帖子最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from posts where type = #{postType}
             */

            //如果selectPostMinAutoIncrementId()返回值为null，证明该类型还没有帖子，直接返回空
            Long postMinAutoIncrementId = postMapper.selectPostMinAutoIncrementId(null, postPageQuery.getPostType());
            if(postMinAutoIncrementId == null)
                return null;

            if(postMinAutoIncrementId > postPageQuery.getCurPageMinId())
                postPageQuery.setCurPageMinId(postMinAutoIncrementId);

            List<Posts> postList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("postType", postPageQuery.getPostType());

            if(postPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", postPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryPostsByTypeAndNavigateToNextPageByIdRange(map);
            }else if(postPageQuery.getNextPage() < 0){
                map.put("curPageMinId", postPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryPostsByTypeAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(postList.isEmpty()) {
                return null;
            }
            
            // 更新点赞数
            postList = updateLatestLikeService.updateObjectLikeCountList(postList);
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = postList.get(0).getAutoIncreasementId();
            long curPageMaxId = postList.get(postList.size()-1).getAutoIncreasementId();
            return new PostPageResponse(postList, new PostPageQuery(curPageMaxId, curPageMinId, 0, null, postPageQuery.getPostType()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询该类型帖子列表失败");
        }
    }

    @Override
    public PostPageResponse getPostsByUserIdWithCursor(PostPageQuery postPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询某用户帖子列表的当前页数最小的id小于
             * 该用户帖子在posts表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果某个用户终于有了第一个帖子，那帖子会被插入到这个posts表格中，
             * 每次posts表格的插入autoIncreasementId+1，
             * 如果这个用户帖子对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这个用户的帖子列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该用户帖子最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from posts where userId = #{userId}
             */

            //如果selectPostMinAutoIncrementId()返回值为null，证明该用户还没有帖子，直接返回空
            Long postMinAutoIncrementId = postMapper.selectPostMinAutoIncrementId(postPageQuery.getUserId(), null);
            if(postMinAutoIncrementId == null)
                return null;

            if(postMinAutoIncrementId > postPageQuery.getCurPageMinId())
                postPageQuery.setCurPageMinId(postMinAutoIncrementId);

            List<Posts> postList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);
            map.put("userId", postPageQuery.getUserId());

            if(postPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", postPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryPostsByUserIdAndNavigateToNextPageByIdRange(map);
            }else if(postPageQuery.getNextPage() < 0){
                map.put("curPageMinId", postPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryPostsByUserIdAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(postList.isEmpty()) {
                return null;
            }
            
            // 更新点赞数
            postList = updateLatestLikeService.updateObjectLikeCountList(postList);
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = postList.get(0).getAutoIncreasementId();
            long curPageMaxId = postList.get(postList.size()-1).getAutoIncreasementId();
            return new PostPageResponse(postList, new PostPageQuery(curPageMaxId, curPageMinId, 0, postPageQuery.getUserId(), null));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询该用户帖子列表失败");
        }
    }

    @Override
    public PostPageResponse queryLatestPostsWithCursor(PostPageQuery postPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询最新帖子列表的当前页数最小的id小于
             * 帖子在posts表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果系统中终于有了第一个帖子，那帖子会被插入到这个posts表格中，
             * 每次posts表格的插入autoIncreasementId+1，
             * 如果这个帖子对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询最新帖子列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取最新帖子最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from posts
             */

            //如果selectPostMinAutoIncrementId()返回值为null，证明系统还没有帖子，直接返回空
            Long postMinAutoIncrementId = postMapper.selectPostMinAutoIncrementId(null, null);
            if(postMinAutoIncrementId == null)
                return null;

            if(postMinAutoIncrementId > postPageQuery.getCurPageMinId())
                postPageQuery.setCurPageMinId(postMinAutoIncrementId);

            List<Posts> postList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);

            if(postPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", postPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryLatestPostsAndNavigateToNextPageByIdRange(map);
            }else if(postPageQuery.getNextPage() < 0){
                map.put("curPageMinId", postPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryLatestPostsAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(postList.isEmpty()) {
                return null;
            }
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = postList.get(0).getAutoIncreasementId();
            long curPageMaxId = postList.get(postList.size()-1).getAutoIncreasementId();
            return new PostPageResponse(
                    updateLatestLikeService.updateObjectLikeCountList(postList), new PostPageQuery(curPageMaxId, curPageMinId, 0, null, null));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询最新帖子列表失败");
        }
    }

    @Override
    public PostPageResponse queryMostViewedPostsWithCursor(PostPageQuery postPageQuery) {
        try {
            /**
             * 严格按照follow分页的实现方式
             * 如果刚开始查询热门帖子列表的当前页数最小的id小于
             * 帖子在posts表格中实际最小的autoIncreasementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果系统中终于有了第一个帖子，那帖子会被插入到这个posts表格中，
             * 每次posts表格的插入autoIncreasementId+1，
             * 如果这个帖子对应的 autoIncreasementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询热门帖子列表，curPageMinId=0，的话，数据库需要从索引的起点（即 autoIncreasementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncreasementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取热门帖子最小的autoIncreasementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncreasementId) from posts
             */

            //如果selectPostMinAutoIncrementId()返回值为null，证明系统还没有帖子，直接返回空
            Long postMinAutoIncrementId = postMapper.selectPostMinAutoIncrementId(null, null);
            if(postMinAutoIncrementId == null)
                return null;

            if(postMinAutoIncrementId > postPageQuery.getCurPageMinId())
                postPageQuery.setCurPageMinId(postMinAutoIncrementId);

            List<Posts> postList;
            Map map = new HashMap();
            map.put("pageSize", PAGE_SIZE);

            if(postPageQuery.getNextPage() > 0){
                map.put("curPageMaxId", postPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize", postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryMostViewedPostsAndNavigateToNextPageByIdRange(map);
            }else if(postPageQuery.getNextPage() < 0){
                map.put("curPageMinId", postPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize", -postPageQuery.getNextPage() * PAGE_SIZE);
                postList = postMapper.queryMostViewedPostsAndNavigateToPreviousPageByIdRange(map);
            }else {
                return null;
            }
            
            if(postList.isEmpty()) {
                return null;
            }
            
            // 更新点赞数
            postList = updateLatestLikeService.updateObjectLikeCountList(postList);
            
            // 严格按照follow分页的方式更新游标位置
            long curPageMinId = postList.get(0).getAutoIncreasementId();
            long curPageMaxId = postList.get(postList.size()-1).getAutoIncreasementId();
            return new PostPageResponse(postList, new PostPageQuery(curPageMaxId, curPageMinId, 0, null, null));
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询热门帖子列表失败");
        }
    }
}