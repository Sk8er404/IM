package org.com.code.im.service.session.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.FollowMapper;
import org.com.code.im.mapper.PrivateMemberMapper;
import org.com.code.im.mapper.SessionMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.pojo.*;
import org.com.code.im.pojo.query.FanListPageQuery;
import org.com.code.im.pojo.UserFan;
import org.com.code.im.pojo.dto.UserFanListResponse;
import org.com.code.im.pojo.UserFollowing;
import org.com.code.im.service.session.FollowService;
import org.com.code.im.utils.FriendManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FollowImpl implements FollowService {

    private static final int PAGE_SIZE=10;

    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PrivateMemberMapper privateMemberMapper;
    @Autowired
    private SessionMapper sessionMapper;

    @Override
    @Transactional
    public void insertFollow(long userId,long fanId) {

        try{
            /**
             * 判断是否已经关注过该用户，如果已经关注过，则直接返回
             */
            if(FriendManager.checkIfUser1FollowUser2(fanId,userId))
                return;
            /**
             * 1.更新用户的位图里面的好友列表，用于实时好友验证
             * 2.更新follows表格，添加关注关系
             */
            FriendManager.user1FollowUser2(fanId,userId);
            followMapper.insertFollow(new Follows(userId,fanId,null));
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("关注功能重复插入主键");
        }
    }

    @Override
    @Transactional
    public void cancelFollow(long userId,long fanId) {

        try {
            /**
             * 判断是否已经关注过该用户，如果本来就没有关注过，则直接返回
             */
            if(!FriendManager.checkIfUser1FollowUser2(fanId,userId))
                return;
            /**
             * 移除关注关系
             * 1.更新用户的位图里面的好友列表，用于实时好友验证
             * 2.更新follows表格，删除关注关系
             */
            FriendManager.removeFollowing(fanId,userId);
            followMapper.cancelFollow(new Follows(userId,fanId,null));
        }catch (Exception e){
            throw new DatabaseException("未知数据库错误");
        }
    }

    @Override
    public UserFanListResponse queryFanList(FanListPageQuery fanListPageQuery) {
        try {
            /**
             * 如果刚开始查询某位用户粉丝列表的当前页数最小的id小于
             * 用户在follows表格中实际最小的autoIncrementId,
             * 则将当前页最小的id设置为实际的最小的自增id
             *
             * 举个例子：
             * 如果一个用户终于有了自己的第一个粉丝，那他和这位粉丝的id会被插入到这个follows表格中，
             * 每次follows表格的插入autoIncrementId+1，
             * 如果这位用户对应的 autoIncrementId 的最小值是几百万（如 5000000），
             * 此时如果第一次查询这位用户的粉丝列表，curPageMinId=0，的话，据库需要从索引的起点（即 autoIncrementId 的最小值）开始扫描，
             * 直到找到满足条件的记录，那么数据库需要跳过大量的无效记录（autoIncrementId <= 0），这会导致额外的 I/O 操作
             * 所以此处我先用以下方法获取该用户最小的autoIncrementId，然后再和第一次的curPageMinId进行比较赋值，这样效率更高一点
             * select min(autoIncrementId) from follows
             *         where userId = #{userId}
             */

            //如果selectUserFanMinAutoIncreaseId()返回值为null，证明该用户还没有粉丝，直接返回空
            Long userMinAuoIncreaseId= followMapper.selectUserFanMinAutoIncreaseId(fanListPageQuery.getUserId());
            if(userMinAuoIncreaseId==null)
                return null;

            if(userMinAuoIncreaseId>fanListPageQuery.getCurPageMinId())
                fanListPageQuery.setCurPageMinId(userMinAuoIncreaseId);

            List<UserFan> userFanList;
            Map map=new HashMap();
            map.put("pageSize",PAGE_SIZE);


            if(fanListPageQuery.getUserId()==null)
                map.put("userId",Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()));
            else
                map.put("userId", fanListPageQuery.getUserId());

            if(fanListPageQuery.getNextPage()>0){

                map.put("curPageMaxId",fanListPageQuery.getCurPageMaxId());
                map.put("nextPageMultiplyPageSize",fanListPageQuery.getNextPage()*PAGE_SIZE);
                userFanList=followMapper.queryFanListAndNavigateToNextPageByIdRange(map);

            }else if(fanListPageQuery.getNextPage()<0){

                map.put("curPageMinId",fanListPageQuery.getCurPageMinId());
                map.put("nextPageMultiplyPageSize",-fanListPageQuery.getNextPage()*PAGE_SIZE);
                userFanList=followMapper.queryFanListAndNavigateToPreviousPageByIdRange(map);

            }else {
                return null;
            }
            long curPageMinId=userFanList.get(0).getAutoIncreasementId();
            long curPageMaxId=userFanList.get(userFanList.size()-1).getAutoIncreasementId();
            return new UserFanListResponse(userFanList,new FanListPageQuery(curPageMaxId,curPageMinId,0,null));
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("未知数据库错误,查询粉丝列表失败");
        }
    }

    /**
     *由于大部分用户关注的人数最多在几十个到百个,这些比粉丝列表的数据量少,所以就不用优化后端分页查询了,
     *直接返回所有用户正在关注的人的列表
     */
    @Override
    public List<UserFollowing> queryFollowList(long  userId) {
        try {
            return followMapper.queryFollowList(userId);
        }catch (Exception e){
            throw new DatabaseException("未知数据库错误,查询关注列表失败");
        }
    }

    @Override
    public List<UserFollowing> queryFriendList(long userId) {
       try {
           /**
            * 通过用户的关注列表的位图和用户的粉丝列表的位图,
            * 两个位图的交集求得该用户的好友列表
            */
           long[] friendIds = FriendManager.getFriendList(userId);
           if(friendIds==null||friendIds.length==0)
               return null;
           List<Long> friendIdList=Arrays.stream(friendIds).boxed().collect(Collectors.toList());
           List<UserNameAndAvatar> friendNameAndAvatarList = userMapper.selectNameAndAvatarByIds(friendIdList);

           List<UserFollowing> friendList = new ArrayList<>();
           for (int i = 0; i < friendIds.length; i++)
               friendList.add(new UserFollowing(friendIds[i],friendNameAndAvatarList.get(i).getUserName(),friendNameAndAvatarList.get(i).getAvatar()));
           return friendList;
       }catch (Exception e){
           e.printStackTrace();
           throw new DatabaseException("未知数据库错误,查询好友列表失败");
       }

    }
}
