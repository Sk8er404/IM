package org.com.code.im.service;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LikeOperationService {
    @Autowired
    private VideoLikeMapper videoLikeMapper;
    @Autowired
    private PostLikeMapper postLikeMapper;
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private PostCommentLikeMapper postCommentLikeMapper;

    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate<String, Long> redisTemplate;


    class OperationType {
        String type;

        String objectId;
        String userId;

        public String Object_User;
        public String User_Object;
        public String UserList;
        public String ObjectList;

        /**
         * 这个字段主要是为给以下场景设计的:
         * 用户给某个视频或者帖子底下的某个评论点赞,但是如果有一天,视频和帖子被删除了
         * 此时,为了删干净,还要把该帖子的所有评论点赞记录也删除,也包括该帖子底下的一些评论自己的点赞记录
         * 所以,这个字段是用来存储视频或者帖子的id的,当视频或者帖子被删除的时候,
         * 可以用来删除该视频或者帖子的所有评论点赞记录
         */
        public String associatedVideoOrPostIdForCommentLikes;

        public List<Long> getIdList(long userId){
            if(this.type.equals("video"))
                return videoLikeMapper.queryLikedVideoList(userId);
            else
                return postLikeMapper.queryLikedPostList(userId);
        }

        public List<Object> selectObjectListByManyIds(List<Long> finalList){
            if(this.type.equals("video"))
                return videoMapper.selectVideoListByManyIds(finalList);
            else
                return postMapper.selectPostListByManyIds(finalList);
        }

        public void insertObjectLike(List<Map<String, Long>> addList){
            if(this.type.equals("video"))
                videoLikeMapper.insertVideoLike(addList);
            else if(this.type.equals("post"))
                postLikeMapper.insertPostLike(addList);
            else
                postCommentLikeMapper.insertPostCommentLike(addList);
        }

        public void deleteObjectLike(List<Map<String, Long>> deleteList){
            if(this.type.equals("video"))
                videoLikeMapper.deleteVideoLike(deleteList);
            else if(this.type.equals("post"))
                postLikeMapper.deletePostLike(deleteList);
            else
                postCommentLikeMapper.deletePostCommentLike(deleteList);
        }

        public void updateObjectLike(List<Map<String, Long>> updateObjectLikeList){
            if(this.type.equals("video"))
                videoLikeMapper.updateVideoLikes(updateObjectLikeList);
            else if(this.type.equals("post"))
                postLikeMapper.updatePostLikes(updateObjectLikeList);
            else
                postCommentLikeMapper.updatePostCommentLikes(updateObjectLikeList);
        }

        public boolean checkIfUserAlreadyGiveLike(Map map){
            if(operationType.type.equals("video"))
                return videoLikeMapper.checkIfUserAlreadyGiveLike(map);
            else if(operationType.type.equals("post"))
                return postLikeMapper.checkIfUserAlreadyGiveLike(map);
            else
                return postCommentLikeMapper.checkIfUserAlreadyGiveLike(map);
        }
    }

    public OperationType operationType=new OperationType();

    public LikeOperationService init(String type,String objectId,String userId,
                                String Object_User,String User_Object,String ObjectList,String UserList){
        this.operationType.type = type;
        this.operationType.objectId = objectId;
        this.operationType.userId = userId;

        this.operationType.Object_User = Object_User;
        this.operationType.User_Object = User_Object;
        this.operationType.ObjectList = ObjectList;
        this.operationType.UserList = UserList;

        return this;
    }

    @Bean("getVideoOperation")
    public LikeOperationService getVideoOperation(){
        LikeOperationService operationService = new LikeOperationService();
        /**
         * operationType的成员变量: type,objectId,userId不同类别的操作"名字"可以一样
         * 但是Object_User,User_Object,ObjectList,UserList不同类别的操作"名字"不能一样
         */
        return operationService.init(
                "video","videoId","userId",
                "Video_User","User_Video","VideoList","UserList_Video"
        );
    }
    @Bean("getPostOperation")
    public LikeOperationService getPostOperation(){
        LikeOperationService operationService = new LikeOperationService();
        return operationService.init(
                "post","postId","userId",
                "Post_User","User_Post","PostList","UserList_Post"
        );
    }

    @Bean("getPostCommentOperation")
    public LikeOperationService getPostCommentOperation(){
        LikeOperationService operationService = new LikeOperationService();
        operationService.init(
                "postComment","postCommentId","userId",
                "PostComment_User","User_PostComment","PostCommentList","UserList_PostComment"
       );
        operationService.operationType.associatedVideoOrPostIdForCommentLikes="superObjectId";
        return operationService;
    }

    private static int pageSize = 10;
    /**
     * 我点赞和取消点赞的逻辑的前提,
     * redis作为缓存层,不存储所有对象的所有点赞记录,所有点赞记录由mysql存储
     * 用户进行的点赞和取消点赞的操作暂时存储在redis中,每隔一段时间把记录统一批量同步到mysql中,然后删除redis中的暂存的记录
     * 这就有一个问题,如果一个人很久之前就给一个对象点赞了,后来他想要取消点赞,但是redis只是暂时缓存他最近的点赞或取消点赞的操作,
     * 而不是很久以前的点赞记录,所以只根据redis的记录不知道用户是否已经给该对象点赞过,
     * 所以,我此处的逻辑如下:
     *
     */

    public void insertLike(Long associatedVideoOrPostIdForCommentLikes,Long objectId, Long userId) {
        try {
            /**
             * 插入点赞记录的时候, 先判断redis中是否有用户点赞的记录
             * hash: Object_User   userId    1/-1(表示用户是否点赞过该对象，1表示点赞过，-1表示没有点赞过)
             */
            Object obj = redisTemplate.opsForHash().get(operationType.Object_User + objectId, String.valueOf(userId));
            /**
             * 如果redis中没有任何该用户是否已经点赞或者取消点赞的记录
             * 则先查询数据库是否存在该用户对该对象的点赞记录
             */
            if(obj == null){
                Map map=new HashMap();
                map.put(operationType.objectId,objectId);
                map.put(operationType.userId,userId);
                /**
                 * 如果redis中该用户没有点赞过该对象,则插入一条点赞记录到redis中,同时把对应对象的点赞记录加1
                 */
                if(!operationType.checkIfUserAlreadyGiveLike(map)){
                    redisTemplate.opsForHash().put(operationType.Object_User + objectId, String.valueOf(userId), 1);

                    redisTemplate.opsForHash().put(operationType.User_Object+userId, String.valueOf(objectId), 1);
                    redisTemplate.opsForSet().add(operationType.UserList, userId);

                    redisTemplate.opsForZSet().incrementScore(operationType.ObjectList,objectId, 1);
                    
                    /**
                     * 如果数据库不存在该点赞记录,则把该点赞记录的objectId和associatedVideoOrPostIdForCommentLikes放到redis中
                     */
                    if(operationType.associatedVideoOrPostIdForCommentLikes!=null)
                        redisTemplate.opsForHash().put(operationType.associatedVideoOrPostIdForCommentLikes, String.valueOf(objectId), associatedVideoOrPostIdForCommentLikes);
                }
            }/**
             * 如果redis中有用户取消点赞的操作记录,则把取消点赞这个操作的记录删除,当作什么也没发生,然后把对应对象的点赞记录加1
             */
            else if ((long)obj==-1) {
                redisTemplate.opsForHash().delete(operationType.Object_User + objectId, String.valueOf(userId));
                redisTemplate.opsForHash().delete(operationType.User_Object+userId, String.valueOf(objectId));
                redisTemplate.opsForZSet().incrementScore(operationType.ObjectList, objectId, 1);
            }
            /**
             * 如果是其他情况,obj为1在redis中表示该用户已经点赞过该对象,则直接返回,不需要再插入一条点赞记录到redis中,
             */
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("点赞失败");
        }
    }

    public void deleteLike(long objectId, long userId) {
        try {
            /**
             * 取消点赞的逻辑同点赞的逻辑
             */
            Object obj = redisTemplate.opsForHash().get( operationType.Object_User+ objectId, String.valueOf(userId));
            if(obj==null){
                Map map=new HashMap();
                map.put(operationType.objectId,objectId);
                map.put(operationType.userId,userId);

                if(operationType.checkIfUserAlreadyGiveLike(map)){
                    redisTemplate.opsForHash().put(operationType.Object_User + objectId, String.valueOf(userId), -1);

                    redisTemplate.opsForHash().put(operationType.User_Object+userId, String.valueOf(objectId),-1);
                    redisTemplate.opsForSet().add(operationType.UserList, userId);

                    redisTemplate.opsForZSet().incrementScore(operationType.ObjectList, objectId, -1);
                }
            }else if ((long)obj==1) {
                redisTemplate.opsForHash().delete(operationType.Object_User + objectId, String.valueOf(userId));
                redisTemplate.opsForHash().delete(operationType.User_Object+userId, String.valueOf(objectId));
                redisTemplate.opsForZSet().incrementScore(operationType.ObjectList, objectId, -1);

                /**
                 * 如果数据库存在该点赞记录,则把该点赞记录的objectId和associatedVideoOrPostIdForCommentLikes从redis中删除
                 */
                if(operationType.associatedVideoOrPostIdForCommentLikes!=null)
                    redisTemplate.opsForHash().delete(operationType.associatedVideoOrPostIdForCommentLikes, String.valueOf(objectId));
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("取消点赞失败");
        }
    }

    //pageNum从1开始,pageSize为10
    public List<Object> queryLikedList(long userId,int pageNum) {
        try {
            /**
             * 先从数据库中查询用户喜欢的对象的id集合
             */
            List<Long> idList = operationType.getIdList(userId);
            /**
             * 如果redis中没有缓存用户对对象点赞和删除的任何一次操作记录,则直接返回数据库中查询到的对象集合
             */
            if(!redisTemplate.opsForSet().isMember(operationType.UserList, userId))
                return getLikedObjectInPage(pageNum, idList);
            Map<Object, Object> idMap = redisTemplate.opsForHash().entries(operationType.User_Object+userId);
            if(idMap==null||idMap.isEmpty())
                return getLikedObjectInPage(pageNum, idList);
            /**
             * 如果redis中有缓存用户对对象点赞和删除的任何一次操作记录,则把数据库中查询到的对象集合和redis中的点赞记录进行合并
             * 先把数据库中查询到的用户的点赞过的对象Id的List集合转换为Set集合,
             * 因为添加和删除Set的元素时间复杂度为1,比List集合快多了
             */
            Set<Long> finalObjectIdSet = new HashSet<>(idList);
            idMap.forEach((key, value) -> {
                /**
                 * 如果是1,则表示缓存的是用户给对象点赞的操作,所以把该对象Id加入到finalObjectIdSet集合中
                 */
                if((long)value==1)
                    finalObjectIdSet.add(Long.parseLong((String)key));
                /**
                 * 如果是-1,则表示缓存的是用户取消点赞的操作,所以把该对象Id从finalObjectIdSet集合中移除
                 */
                else if((long)value==-1){
                    finalObjectIdSet.remove(Long.parseLong((String)key));
                }
            });
            /**
             * 把合并后的集合转换为List集合,并返回
             */
            if(finalObjectIdSet.isEmpty())
                return null;
            return getLikedObjectInPage(pageNum,new ArrayList<>(finalObjectIdSet));
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("查询自己喜欢的列表失败");
        }
    }

    private List<Object> getLikedObjectInPage(int pageNum, List<Long> objectIdList) {
        if(objectIdList==null||objectIdList.isEmpty())
            return new ArrayList<>();
        // 计算起始索引和结束索引
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, objectIdList.size());

        List<Long> finalVideoIdList = objectIdList.subList(startIndex, endIndex);
        return operationType.selectObjectListByManyIds(finalVideoIdList);
    }

    /**
     * 每半小时同步一次点赞记录到mysql中,然后删除redis中的暂存的记录
     */
    public void synchronizeRedisAndMysql() {
        /**
         * 存储的数据结构是
         * ZSet: ObjectList     objectId   delta(表示这个对象在这段时间内的对象点赞增量，可以为负数)
         * Hash: Object_User    userId    1/-1(表示用户是否点赞过该对象，1表示点赞过，-1表示没有点赞过)
         */
        Set<ZSetOperations.TypedTuple<Long>> objectIdWithScore=null;
        try {
            objectIdWithScore = redisTemplate.opsForZSet().rangeWithScores(operationType.ObjectList, 0, -1);
            if (objectIdWithScore == null || objectIdWithScore.isEmpty()) {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Map<String, Long>> addList = new LinkedList<>();
        List<Map<String, Long>> deleteList = new LinkedList<>();

        List<Long> objectIdList = new LinkedList<>();
        List<Long> userIdList = new LinkedList<>();
        List<Map<String, Long>> updateObjectLikeList = new LinkedList<>();

        Map<Long,Long> associatedVideoOrPostIdForCommentLikesMap=new HashMap<>();
        if(operationType.associatedVideoOrPostIdForCommentLikes!=null){
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(operationType.associatedVideoOrPostIdForCommentLikes);
                entries.forEach((key, value) -> {
                    associatedVideoOrPostIdForCommentLikesMap.put(Long.parseLong(key.toString()), Long.parseLong(value.toString()));
                });
        }

        /**
         * 遍历ZSet集合 ObjectList  objectId  delta的数据
         */
        for (ZSetOperations.TypedTuple<Long> tuple : objectIdWithScore) {
            Long objectId = tuple.getValue();
            Double score = tuple.getScore();

            objectIdList.add(objectId);

            /**
             * 把每一个objectId和对应的delta(点赞的增量或者减量数据)放到一个map中，然后把这个map放到一个list中，
             */
            Map<String, Long> updateObjectLikeMap = new HashMap<>();
            updateObjectLikeMap.put(operationType.objectId, objectId);
            updateObjectLikeMap.put("delta", score.longValue());

            updateObjectLikeList.add(updateObjectLikeMap);

            /**
             * 遍历Hash集合 Object_User userId  1/-1的数据
             */
            Map<Object, Object> objectMap = redisTemplate.opsForHash().entries(operationType.Object_User + objectId);
            for (Object userId : objectMap.keySet()) {
                Long userIdNumber=Long.parseLong(userId.toString());
                userIdList.add(userIdNumber);
                /**
                 * 上面遍历了ZSet集合的数据,根据获取到的ObjectId,通过
                 * Object_User userId  1/-1的数据,获取到每个userId,然后把这个objectId及其对应的userId放到一个map中，
                 */
                Map<String, Long> map = new HashMap<>();
                map.put(operationType.objectId,objectId);
                map.put(operationType.userId, userIdNumber);
                /**
                 * 如果associatedVideoOrPostIdForCommentLikesMap中存在该objectId,
                 * 则把该objectId和对应的associatedVideoOrPostIdForCommentLikes放到一个map中，然后把这个map放到一个list中，
                 */
                if(associatedVideoOrPostIdForCommentLikesMap.containsKey(objectId)){
                    map.put("superObjectId", associatedVideoOrPostIdForCommentLikesMap.get(objectId));
                }

                /**
                 * 根据每一对的objectId 和 userId,对应的值,
                 * 如果是 1 则表示userId用户要给ObjectId的对象进行点赞操作,然后把map放到addList中
                 * 如果是 -1 则表示userId用户要给ObjectId的对象进行取消点赞操作,然后把map放到deleteList中
                 */
                if ((Long) objectMap.get(userId) == 1) {
                    addList.add(map);
                } else if ((Long) objectMap.get(userId) == -1) {
                    deleteList.add(map);
                }
            }
        }
        /**
         * 最后根据addList和deleteList,把objectId和userId这对值所代表的点赞记录插入到mysql中,或者从mysql中删除
         * 然后再删除redis中的暂存的操作记录
         */
        if (addList.size() > 0)
            operationType.insertObjectLike(addList);
        if (deleteList.size() > 0)
            operationType.deleteObjectLike(deleteList);

        operationType.updateObjectLike(updateObjectLikeList);
        redisTemplate.delete(operationType.ObjectList);
        redisTemplate.delete(operationType.UserList);
        if(operationType.associatedVideoOrPostIdForCommentLikes!=null)
            redisTemplate.delete(operationType.associatedVideoOrPostIdForCommentLikes);
        cleanUpRedisKeyList(objectIdList,operationType.Object_User);
        cleanUpRedisKeyList(userIdList,operationType.User_Object);

    }

    public void cleanUpRedisKeyList(List<Long> idList,String keyName){
        redisTemplate.executePipelined(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long id : idList) {
                    byte[] key = redisTemplate.getStringSerializer().serialize(keyName + id);
                    connection.del(key);
                }
                return null;
            }
        });
    }
}
