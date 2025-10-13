package org.com.code.im.controller.session;

import org.com.code.im.exception.BadRequestException;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.pojo.ConsumerMessageType;
import org.com.code.im.pojo.dto.CreateSessionOrInviteRequest;
import org.com.code.im.pojo.Sessions;
import org.com.code.im.rocketMq.producer.MsgProducer;
import org.com.code.im.service.session.SessionService;
import org.com.code.im.utils.FriendManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.这里的应用层接口有些是异步通过ws返回结果,web的普通http请求只会返回等待的通知,比如这样:
 * {
 *     messageStatus: "pending"
 *     sequenceId: 0
 *     message: "群聊创建中"
 * }
 *
 * 真正的结果是异步返回的,前端异步通过ws连接来接收到消息,再进行页面更新,ws返回结果比如:
 * {
 *     "code": 200,
 *     "message": "创建群聊"
 * }
 * {
 *     "code": 500,
 *     "message": "群聊创建"
 * }
 *
 * 上述的message字段是前端判断信息类别的标识符,不同接口异步返回的信息都有自己唯一的标识符
 *
 * 2.有些则是同步返回的,web的普通http请求直接获取返回结果
 *
 * 所以我现在在以下的每一个方法开头标注:
 *      1. 异步: 返回的接口，返回结果是异步返回的，前端通过ws接收到消息,再进行页面更新
 *      2. 同步: 返回的接口，返回结果是同步返回的，前端直接通过接口获取返回结果
 */

@RestController
public class SessionController {
    @Autowired
    private SessionService sessionService;
    @Autowired
    private MsgProducer msgProducer;
    @Qualifier("redisTemplateLong")
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 用户传上来的参数是CreateSessionRequest类，包含
     *
     *     private Long ownerId; 用户自己的id,前端不用写这个，留空就行
     *     private Long[] userIds; 群聊的成员id数组，不包括自己
     *     private Sessions session;携带群头像，群名称
     *
     *  异步返回结果
     */
    @PostMapping("/api/session/createGroup")
    public ResponseHandler CreateGroup(@RequestBody CreateSessionOrInviteRequest createSessionOrInviteRequest) throws BadRequestException {

        if(createSessionOrInviteRequest.getUserIds().length<2|| createSessionOrInviteRequest.getUserIds().length>99) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"群聊人数最少为3个人，最多为200个人");
        }
        if(createSessionOrInviteRequest.getSession().getGroupName()==null)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"群聊名称不能为空");

        /**
         * 创建群聊还有一个特殊的要求是,只有双方互相关注的情况下，即双方是好友的情况下
         * 一方才可以邀请另一方创建群聊
         */

        long ownerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Long> notFriendIdListIds = getNotFriendMembers(ownerId, createSessionOrInviteRequest);
        /**
         * 创建群聊的时候，前端会优先从好友中选择创建群聊的成员列表，
         * 非好友成员不太可能会出现在创建群聊的成员的选择列表中
         * 如果真的出现这种情况，则返回错误信息
         */
        if(notFriendIdListIds.size()>0){
            String notFriendIds = String.join(", ", notFriendIdListIds.stream().map(String::valueOf).toArray(String[]::new));
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "出现以下非好友关系成员，无法创建群聊: " + notFriendIds);
        }

        /**
         * 把建群的请求发给消息队列，异步建群
         *
         * SecurityContextHolder 是 Spring Security 提供的工具类，用于存储当前用户的认证信息。它的默认实现是基于 ThreadLocal 的，这意味着它只在同一个线程中有效。
         *
         * 在 Web 请求中：当用户携带 Token 访问你的 API 时，Spring Security 会解析 Token 并将认证信息存储到 SecurityContextHolder 中。
         * 因此，在处理该请求的线程中，你可以直接调用 SecurityContextHolder.getContext().getAuthentication().getName()。
         * 在异步操作中（如消息队列）：当你将请求消息发送到消息队列后，消费者的处理逻辑通常运行在不同的线程中。
         * 此时，SecurityContextHolder 的内容不会自动传播到消费者线程中，因此你无法直接调用 SecurityContextHolder.getContext().getAuthentication().getName()。
         *
         * 所以这里要提前通过上下文获取id，设置到createGroupRequest中，再发到消息队列
         */
        createSessionOrInviteRequest.setOwnerId(ownerId);
        createSessionOrInviteRequest.setRequestType("createGroup");
        msgProducer.sendGroupCreateMessage(createSessionOrInviteRequest);

        return new ResponseHandler(ResponseHandler.PROCESSING,"群聊创建中");
    }

    private List<Long> getNotFriendMembers(long ownerId, CreateSessionOrInviteRequest createSessionOrInviteRequest) {

        List<Long> notFriendIdListIds = new ArrayList<>();
        /**
         * 遍历创建群聊时候的成员id，如果其中一个不是好友,则把userId加入notFriendIdListIds中
         * 同时设置appearNotFriendObject为true
         */
        for (Long userId : createSessionOrInviteRequest.getUserIds()) {
            if(!FriendManager.areFriends(ownerId,userId)){
                notFriendIdListIds.add(userId);
            }
        }
        return notFriendIdListIds;
    }

    /**
     *
     * 用户传上来的参数是CreateSessionRequest类，包含
     *
     *     private Long ownerId; 用户自己的id,前端不用写这个，留空就行
     *     private Long[] userIds; 邀请入群的群聊的成员id数组，不包括自己
     *     private Sessions session;前端需要写session类里面的sessionId，其他可以不用写
     *
     * 异步返回结果
     */
    @PostMapping("/api/session/inviteUsersToGroup")
    public ResponseHandler inviteUsersToGroup(@RequestBody CreateSessionOrInviteRequest createSessionOrInviteRequest) throws BadRequestException {

        long ownerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        long role = 0;
        if(createSessionOrInviteRequest.getSession().getSessionId()==0)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"请输入群聊id");

        try {
            role = (long)redisTemplate.opsForHash().get("Session_"+ createSessionOrInviteRequest.getSession().getSessionId(),String.valueOf(ownerId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }

        if(role!=2&&role!=1)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"您不是该群的群主或者管理员，无法邀请成员入群");

        if(createSessionOrInviteRequest.getUserIds().length<1|| createSessionOrInviteRequest.getUserIds().length>99) {
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"一次邀请最少1人,最多100人");
        }

        List<Long> notFriendIdListIds = getNotFriendMembers(ownerId, createSessionOrInviteRequest);
        /**
         * 创建群聊的时候，前端会优先从好友中选择创建群聊的成员列表，
         * 非好友成员不太可能会出现在创建群聊的成员的选择列表中
         * 如果真的出现这种情况，则返回错误信息
         */
        if(notFriendIdListIds.size()>0){
            String notFriendIds = String.join(", ", notFriendIdListIds.stream().map(String::valueOf).toArray(String[]::new));
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "无法邀请以下非好友关系成员入群: " + notFriendIds);
        }

        createSessionOrInviteRequest.setOwnerId(ownerId);
        createSessionOrInviteRequest.setRequestType("inviteUsersToGroup");
        msgProducer.sendGroupCreateMessage(createSessionOrInviteRequest);
        
        return new ResponseHandler(ResponseHandler.PROCESSING,"群聊邀请中");
    }

    /**
     * 同步返回结果
     */
    @DeleteMapping("/api/session/kickMemberOutOfGroup")
    public ResponseHandler kickMemberOutOfGroup(@RequestParam("sessionId") long sessionId,@RequestParam("targetUserId") long targetUserId) throws BadRequestException {

        Long ownerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        long roleId;

        try {
            roleId = (long)redisTemplate.opsForHash().get("Session_"+ sessionId,String.valueOf(ownerId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }

        if(roleId!=2&&roleId!=1)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "您不是该群的群主或者管理员，无法踢出成员");

        sessionService.kickOutGroupMember(sessionId,targetUserId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "踢出成员成功");
    }

    /**
     * 同步返回结果
     */
    @DeleteMapping("/api/session/dismissGroup/{sessionId}")
    public ResponseHandler dismissGroup(@PathVariable("sessionId") long sessionId) throws BadRequestException {
        long ownerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        long role;
        try {
            role = (long)redisTemplate.opsForHash().get("Session_"+ sessionId,String.valueOf(ownerId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }

        if(role!=2){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"您不是该群的群主，无法解散群聊");
        }
        /**
         * 由于删除群聊数据的方法是延期一段时间执行的,不会立马调用数据库,所以我不用消息队列
         * 但是把redis中所有该群id和群成员的userId对应关系的键值会被马上删除,
         * 所以下述操作一旦执行,群成员就马上无法在群里面发送和接收消息,也就相当于立马解散了
         */
        sessionService.dismissGroup(sessionId);

        return new ResponseHandler(ResponseHandler.SUCCESS, "群聊解散成功");
    }


    /**
     * 异步返回结果
     */
    @PostMapping("/api/session/getPrivateConversation/{targetId}")
    public ResponseHandler createOrGetCurrentPrivateConversation(@PathVariable("targetId") long targetId) throws BadRequestException {

        if(targetId<=0){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"targetId不能小于等于0");
        }

        if(!redisTemplate.opsForSet().isMember("AllUserId",targetId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "查询的用户不存在");

        /**
         * 把建立用户之间的私人会话的请求发给消息队列，异步建立私人对话
         */
        CreateSessionOrInviteRequest createSessionOrInviteRequest = new CreateSessionOrInviteRequest();
        createSessionOrInviteRequest.setOwnerId(Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()));
        createSessionOrInviteRequest.setUserIds(new Long[]{targetId});
        msgProducer.sendPrivateSessionCreateMessage(createSessionOrInviteRequest);

        return new ResponseHandler(ResponseHandler.PROCESSING, "正在建立私聊");
    }


    /**
     * 异步返回结果
     */
    @GetMapping("/api/session/queryGroupInfo/{sessionId}")
    public ResponseHandler queryGroupInfo(@PathVariable("sessionId") long sessionId) throws BadRequestException {

        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        /**
         * 把查询群聊信息的请求发给消息队列，异步查询群聊信息
         * ConsumerMessageType的-1表示占位符,没有意义
         */
        ConsumerMessageType messageType = new ConsumerMessageType(sessionId,userId,"groupInfo");
        msgProducer.sendQuerySessionMessage(messageType);

        return new ResponseHandler(ResponseHandler.PROCESSING, "正在查询群聊信息");
    }

    /**
     *  异步返回结果
     *  查询群聊列表
     */
    @GetMapping("/api/session/queryGroupSessionList")
    public ResponseHandler queryGroupList() throws BadRequestException {

        /**
         * 先验证用户是否登录
         */
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        /**
         * 把查询群聊列表请求发送到消息队列,异步查询群聊列表
         * 返回结果通过websocket发送给客户端
         */
        ConsumerMessageType messageType = new ConsumerMessageType(-1,userId,"groupSessionList");
        msgProducer.sendQuerySessionMessage( messageType);

        return new ResponseHandler(ResponseHandler.PROCESSING, "正在查询群聊列表");
    }

    /**
     * 异步返回结果
     */
    @GetMapping("/api/session/queryPrivateSessionList")
    public ResponseHandler queryPrivateSessionList() throws BadRequestException {

        /**
         * 先验证用户是否登录
         */
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        /**
         * 把查询私聊列表请求发送到消息队列,异步查询私聊列表
         * 返回结果通过websocket发送给客户端
         */
        ConsumerMessageType messageType = new ConsumerMessageType(-1,userId,"privateSessionList");
        msgProducer.sendQuerySessionMessage(messageType);

        return new ResponseHandler(ResponseHandler.PROCESSING, "正在查询私聊列表");
    }


    /**
     * 异步返回结果
     * 查询群聊成员列表
     */
    @GetMapping("/api/session/queryGroupMemberList/{sessionId}")
    public ResponseHandler queryGroupMemberList(@PathVariable("sessionId") long sessionId) throws BadRequestException {

        /**
         * 先验证用户是否登录
         */
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        ConsumerMessageType messageType = new ConsumerMessageType(sessionId,userId,"groupMemberList");
        msgProducer.sendQuerySessionMessage(messageType);

        return new ResponseHandler(ResponseHandler.PROCESSING, "正在查询群聊成员列表");
    }

    /**
     * 同步返回结果
     * 查询群聊成员角色,给前端为管理员和普通群成员分别显示功能按钮用的
     * @RequestParam sessionId
     * @return
     * @throws BadRequestException
     */
    @GetMapping("/api/session/queryGroupRole/{sessionId}")
    public ResponseHandler queryGroupRole(@PathVariable("sessionId") long sessionId) throws BadRequestException {

        if(sessionId<=0){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"sessionId不能小于等于0");
        }
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        return new ResponseHandler(ResponseHandler.SUCCESS, "查询群聊成员角色成功",sessionService.queryGroupRole(sessionId,userId));
    }

    /**
     *  同步返回结果
     *  群主或者管理员更新群成员权限接口
     *  可以改变普通群成员的权限为管理员或者改变管理员为普通群成员
     *  只有群主有权限更新群成员权限
     *
     *  role参数只有两种:
     *  1. upgrade To Administrator
     *  2. degrade To Member
     */
    @PutMapping("/api/session/updateMemberRole")
    public ResponseHandler updateMemberRole(@RequestParam("sessionId") long sessionId,@RequestParam("targetUserId") long targetUserId,@RequestParam("role") String role) throws BadRequestException {

        long currentUserId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        long roleId;
        try {
            roleId = (long)redisTemplate.opsForHash().get("Session_"+ sessionId,String.valueOf(currentUserId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }
        if(roleId!=2)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "您不是该群的群主，无法改变他人权限");

        sessionService.updateMemberRole(sessionId,targetUserId,role);
        return new ResponseHandler(ResponseHandler.SUCCESS, "更新群成员权限成功");
    }

    /**
     * 同步返回结果
     * 禁言群成员
     * @RequestParam sessionId
     * @RequestParam targetUserId
     * @return
     * @throws BadRequestException
     */
    @PutMapping("/api/session/MuteGroupMember")
    public ResponseHandler muteMemberInGroup(@RequestParam("sessionId") long sessionId,@RequestParam("targetUserId") long targetUserId) throws BadRequestException {

        long currentUserId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        long roleId;
        try {
            roleId = (long)redisTemplate.opsForHash().get("Session_"+ sessionId,String.valueOf(currentUserId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }
        if(roleId!=2&&roleId!=1)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "您不是该群的群主或者管理员，无法禁言他人");

        sessionService.MuteOrCancelMuteGroupMember(sessionId,currentUserId,targetUserId,"mute member");
        return new ResponseHandler(ResponseHandler.SUCCESS, "禁言成功");
    }

    /**
     * 同步返回结果
     * 取消禁言群成员
     */
    @PutMapping("/api/session/cancelMuteGroupMember")
    public ResponseHandler cancelMuteMemberInGroup(@RequestParam("sessionId") long sessionId,@RequestParam("targetUserId") long targetUserId) throws BadRequestException {

        long currentUserId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        long roleId;
        try {
            roleId = (long)redisTemplate.opsForHash().get("Session_"+ sessionId,String.valueOf(currentUserId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }
        if(roleId!=2&&roleId!=1)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "您不是该群的群主或者管理员，无法取消禁言他人");

        sessionService.MuteOrCancelMuteGroupMember(sessionId,currentUserId,targetUserId,"cancel mute member");
        return new ResponseHandler(ResponseHandler.SUCCESS, "取消禁言成功");
    }

    /**
     * 同步返回结果
     * 用户上传Sessions
     * 但只要填写
     * SessionId(指定哪个群聊需要更新)
     * groupAvatar和groupName即可
     */
    @PutMapping("/api/session/updateGroupInfo")
    public ResponseHandler updateGroupInfo(@RequestBody Sessions sessions) throws BadRequestException {
        long ownerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        long role;
        try {
            role = (long)redisTemplate.opsForHash().get("Session_"+ sessions.getSessionId(),String.valueOf(ownerId));
        }catch (Exception e){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"你不是这个群的成员!!!");
        }

        if(role!=2&&role!=1){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"您不是该群的群主或者管理员,无法修改群信息");
        }
        if(sessions.getGroupName()==null||sessions.getGroupAvatar()==null)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "请填写群名称和群头像");

        sessionService.updateGroupInfo(sessions);
        return new ResponseHandler(ResponseHandler.SUCCESS, "更新群信息成功");
    }
}
