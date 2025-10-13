package org.com.code.im.controller.session;

import org.com.code.im.pojo.query.FanListPageQuery;
import org.com.code.im.pojo.dto.UserFanListResponse;
import org.com.code.im.pojo.UserFollowing;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.session.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 这里应用层的接口是同步返回的,web的普通http请求直接获取返回结果
 */
@RestController
public class FollowController {
    @Autowired
    private FollowService followService;
    @Qualifier("redisTemplateLong")
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     *  用户传上来的参数是
     *  userId
     *  这里的userId就是用户想要关注的人的id
     */

    @PostMapping("/api/follow/FollowThisUser/{userId}")
    public ResponseHandler insertFollow(@PathVariable("userId") long userId) {

        if(userId<=0)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"userId不能小于等于0");
        if(!redisTemplate.opsForSet().isMember("AllUserId",userId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户不存在");

        long fanId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        if(fanId==userId)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"不能关注自己");

        if(redisTemplate.opsForSet().isMember("BlockedUserList_"+userId,userId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户已被屏蔽,请先取消屏蔽再关注");

        followService.insertFollow(userId,fanId);
        return new ResponseHandler(ResponseHandler.SUCCESS,"关注成功");
    }

    /**
     *  用户传上来的参数是
     *  userId
     *  这里的userId就是用户想要取消关注的人的id
     */

    @DeleteMapping("/api/follow/cancelFollow/{userId}")
    public ResponseHandler cancelFollow(@PathVariable("userId") long userId) {

        if(userId<=0){
           return new ResponseHandler(ResponseHandler.BAD_REQUEST,"userId不能小于等于0");
        }

        if(!redisTemplate.opsForSet().isMember("AllUserId",userId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户不存在");

        long fanId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        if(fanId==userId)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"不能取消关注自己");

        followService.cancelFollow(userId,fanId);
        return new ResponseHandler(ResponseHandler.SUCCESS,"取消关注成功");
    }

    /**
     *由于有些人的粉丝很多，不能一次性直接返回全部查询结果,所以需要优化分页查询，一页一页分开查询数据库
     *
     * 这里的参数是类 FanListPageQuery,其参数为:
     * curPageMaxId
     * curPageMinId
     * nextPage
     * userId //userId表示要查询的那个人的粉丝列表的用户id,自己查自己的粉丝列表，则此处填0
     *
     * curPageMaxId表示当前页最大id，curPageMinId表示当前页最小id，
     * 如果nextPage为 5，则表示第从当前页面往下第5页，如果nextPage为-5，则表示当前页面前5页,如果nextPage为0，则什么也不返回
     *
     *
     * 一页默认加载10个粉丝
     * 最开始参数可以默认填写
     * curPageMaxId=0,curPageMinId=0,nextPage=1,userId=你要查询用户粉丝列表的用户id,查询自己的粉丝列表,则userId=0
     *
     * 之后查询返回结果后,数据有2部分:
     * 第1部分是粉丝列表,第二部分是fanListPageQuery类
     * 而返回的fanListPageQuery里面的curPageMaxId,curPageMinId最好作为下一次查询的参数,
     */
    @PostMapping("/api/follow/queryFanList")
    public ResponseHandler queryFanList(@RequestBody FanListPageQuery fanListPageQuery){

        if(fanListPageQuery.getUserId()==0){
            fanListPageQuery.setUserId(Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()));
        }else if(!redisTemplate.opsForSet().isMember("AllUserId", fanListPageQuery.getUserId()))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"查询的用户不存在");

        UserFanListResponse userFanListResponse = followService.queryFanList(fanListPageQuery);

        if(userFanListResponse==null)
            return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功","没有粉丝");
        return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功",userFanListResponse);
    }

    /**
     *前端输入userId则查询userId用户关注了谁的列表,如果userId为0则表示查询自己的关注列表
     */
    @GetMapping("/api/follow/queryFollowList/{userId}")
    public ResponseHandler queryFollowList(@PathVariable("userId") long userId){

        if(userId==0){
            userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        }else if(!redisTemplate.opsForSet().isMember("AllUserId",userId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"查询的用户不存在");
        List<UserFollowing> followList =followService.queryFollowList(userId);
        if(followList==null)
            return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功","没有关注任何人");
        return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功",followList);
    }

    /**
     * 两个人互相关注后,才算好友关系
     */
    @GetMapping("/api/follow/queryFriendList")
    public ResponseHandler queryFriendList(){

        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<UserFollowing> friendList =  followService.queryFriendList(userId);
        if(friendList==null)
            return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功","没有好友");
        return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功",friendList);
    }
}
