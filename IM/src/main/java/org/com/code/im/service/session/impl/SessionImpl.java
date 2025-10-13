package org.com.code.im.service.session.impl;

import jakarta.annotation.PreDestroy;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.*;
import org.com.code.im.pojo.*;
import org.com.code.im.rocketMq.producer.MsgProducer;
import org.com.code.im.service.session.SessionService;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SessionImpl implements SessionService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    SessionMapper sessionMapper;

    @Autowired
    GroupMemberMapper groupMemberMapper;
    @Autowired
    PrivateMemberMapper privateMemberMapper;
    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    MessageMapper messageMapper;

    @Autowired
    private MsgProducer msgProducer;

    /**
     *这个方法是创建私聊会话,如果两个用户之前没有会话,就创建一个会话,如果两个用户之前有会话,就不创建了,返回先前的会话
     */
    @Override
    @Transactional
    public Long createOrGetCurrentPrivateConversation(long ownerId,long userId) {
        /**
         * 创建私聊会话,为了防止反复创建两个相同用户之间的会话，所以需要先判断是否已经存在
         * 而我的私聊会话成员表格是
         * session_id,user_id1,user_id2
         * 所以我只要每次创建表格的时候,每次插入前,先把userId排序,每次都把最大的userId放在前面
         * 然后我查询相同的两个人的私聊会话是否存在时候,再把他们的userId排序,把最大的id在前面,最小的id放在后面
         * 就可以查出这两个人之前是否就有过会话了
         */
        Map<String, Long> orderUserId = orderUserIdByDesc(userId, ownerId);
        Long sessionId;
        sessionId = sessionMapper.selectPrivateSession(orderUserId.get("max"), orderUserId.get("min"));
        if (sessionId != null) {
            return sessionId;
        }
        //创建私聊会话
        Sessions sessions= new Sessions();
        sessions.setSessionId(SnowflakeIdUtil.sessionIdWorker.nextId());
        sessions.setSessionType("private");
        sessions.setCreatedAt(LocalDateTime.now());
        sessionMapper.createSession(sessions);

        //插入私聊会话成员
        privateMemberMapper.insertPrivateMember(new PrivateMembers(sessions.getSessionId(),orderUserId.get("max"),orderUserId.get("min")));

        /**
         * 把私聊会话的两个用户的id都放在redis中，方便日后发送和接收消息时,方便快速找到接收者的id
         *  Private_sessionId userId 0
         *  最初建立对话的时候，值设置为 0
         *
         * Private_sessionId userId的值有不同含义
         *
         * 1. 如果对话双方没有互相关注对方:
         * 假设 user1 和 user2 建立了最初对话,此时对话值为 0,表示user1和user2都可以发消息给对方,但只能发一条消息
         * 如果user1发一条消息给user2,此时就会把user1的值改成-1,表示user1被禁言
         * 如果user2回了一条消息给user1,此时会把user2的值改成-1,表示user2被禁言
         * 如此循环,达到了对方回复之前只允许发送一条信息的功能
         *
         * 2.如果对方互相关注对方: 则双方可以无限发消息
         *
         */
        Map<String,Long> map = new HashMap<>();
        map.put(String.valueOf(userId),0L);
        map.put(String.valueOf(ownerId),0L);
        /**
         * 每一个私聊会话,都会有一个private标识,方便查询这个sessionId是不是私聊对话
         */
        map.put("private",0L);
        redisTemplate.opsForHash().putAll("Session_"+sessions.getSessionId(),map);

        return sessions.getSessionId();
    }

    public Map<String,Long> orderUserIdByDesc(long userId1, long userId2){
        Map<String,Long> map = new HashMap<>();
        if(userId1>userId2){
            map.put("max",userId1);
            map.put("min",userId2);
        }else{
            map.put("max",userId2);
            map.put("min",userId1);
        }
        return map;
    }

    @Override
    @Transactional
    public Long createGroupChat(long ownerId,Long[] userIds, Sessions sessions)throws DatabaseException {
        //创建群会话
        sessions.setSessionId(SnowflakeIdUtil.sessionIdWorker.nextId());
        sessions.setSessionType("group");
        sessions.setOwnerId(ownerId);
        sessions.setCreatedAt(LocalDateTime.now());

        sessionMapper.createSession(sessions);

        Map<String,Long> map = new HashMap<>();
        List<GroupMembers> groupMemberList = new ArrayList<>();
        /**
         * 生成群组成员列表
         *
         * 该方法用于根据用户ID数组和群组信息，生成一个群组成员列表，并记录每个用户加入群组的时间
         * 同时，将每个用户ID映射到一个操作次数（初始为0），存储在Map中
         */
        generateGroupMemberList(sessions.getSessionId(),userIds,groupMemberList,LocalDateTime.now(),map,ownerId);
        groupMemberMapper.insertMembersToGroup(groupMemberList);
        /**
         * 把群的id和群成员的id全部都放在redis中
         * Group_sessionId memberId 0
         * 方便日后发送和接收消息时,方便快速找到接收者的id
         */
        redisTemplate.opsForHash().putAll("Session_"+sessions.getSessionId(),map);

        return sessions.getSessionId();
    }

    @Override
    @Transactional
    public Long addGroupMember(Long sessionId, Long[] userId) {
        List<GroupMembers> groupMemberList = new ArrayList<>();
        Map<String,Long> map = new HashMap<>();

        LocalDateTime joinedTime = LocalDateTime.now();

        generateGroupMemberList(sessionId, userId, groupMemberList, joinedTime, map,0);
        groupMemberMapper.insertMembersToGroup(groupMemberList);
        redisTemplate.opsForHash().putAll("Session_"+sessionId,map);

        return sessionId;
    }

    /**
     * 生成群组成员列表
     *
     * 该方法用于根据用户ID数组和群组信息，生成一个群组成员列表，并记录每个用户加入群组的时间
     * 同时，将每个用户ID映射到一个操作次数（初始为0），存储在Map中
     */
    private void generateGroupMemberList(Long sessionId, Long[] userId, List<GroupMembers> groupMembersList, LocalDateTime joinedTime, Map<String, Long> map,long ownerId) {
        // 初始化群组成员用户名列表
        List<String> GroupMemberNameList;
        // 将用户ID数组转换为列表
        List<Long> GroupMemberIdList = new ArrayList<>(Arrays.asList(userId));
        /**
         *  ownerId不为0则是用于建群时候调用generateGroupMemberList方法
         *         为0则是用于邀请成员入群时候调用generateGroupMemberList方法
         */
        if(ownerId!=0)
            GroupMemberIdList.add(Long.valueOf(ownerId));
        try {
            // 查询群组成员的用户名列表
            if (!GroupMemberIdList.isEmpty()) {
                GroupMemberNameList = userMapper.queryUserNameByManyIds(GroupMemberIdList);
            } else {
                GroupMemberNameList = new ArrayList<>();
            }
        } catch (Exception e) {
            throw new DatabaseException("查询群组成员用户名列表失败");
        }

        /**
         * sessionId memberId1  0
         *           memberId2  2
         *           memberId3  0
         *           ........
         * 然后按照以上格式把群的id和群成员的id关联起来,存入redis中，方便日后发消息时候可以快速找到接收者的id
         *
         * 如果是群主,那么memberId的值设置为为2,群主建群之后,可以通过修改普通member后面的值为1,表明这个member是群管理员
         * 如果是普通member,那么memberId的值设置为为0,如果是被禁言的member,那么memberId的值设置为为-1
         *
         * 初步建群,设置群主的member值为2,其他member的值都为0
         * 邀请其他人入群,最初被邀请的member的值都为0
         */

        int i=0;
        for (long id : userId) {
            // 创建群组成员对象，设置群组会话ID、用户ID、用户名、加入时间和角色
            groupMembersList.add(new GroupMembers(sessionId,id,GroupMemberNameList.get(i), joinedTime,"member"));
            map.put(String.valueOf(id),Long.valueOf(0));
            // 增加索引值，以获取下一个用户名
            i++;
        }
        if(ownerId!=0){
            groupMembersList.add(new GroupMembers(sessionId,ownerId,GroupMemberNameList.get(GroupMemberNameList.size()-1), joinedTime,"owner"));
            map.put(String.valueOf(ownerId),Long.valueOf(2));
        }
    }



    @Override
    public String queryGroupRole(long sessionId,long userId){
        long role;
        try {
            role = (long) redisTemplate.opsForHash().get("Session_"+sessionId,String.valueOf(userId));
        }catch (Exception e){
            return "not in the group or session";
        }
        if(role==2){
            return "owner of the group";
        }else if(role==1){
            return "admin of the group";
        }else if(role==0){
            return "member of the group";
        }else if(role==-1){
            return "muted member of the group";
        }else{
            return "unknown";
        }
    }

    @Override
    public void updateMemberRole(long sessionId, long targetUserId, String role) {
        String message=null,targetUserName=null;
        StringBuilder messageBuilder = new StringBuilder();

        Map map = new HashMap();
        map.put("sessionId",sessionId);
        map.put("userId",targetUserId);

        try{
            targetUserName = userMapper.selectUserNameById(targetUserId);
        }catch (Exception e){
            throw new DatabaseException("数据库异常,成员权限更新失败,请稍后重试");
        }

        if(role.equals("upgrade To Administrator")){
            redisTemplate.opsForHash().put("Session_"+sessionId,String.valueOf(targetUserId),Long.valueOf(1));
            map.put("role","admin");

            message=messageBuilder.append(targetUserName).append(" 被升级成管理员").toString();
        }else if(role.equals("degrade To Member")){
            redisTemplate.opsForHash().put("Session_"+sessionId,String.valueOf(targetUserId),Long.valueOf(0));
            map.put("role","member");

            message=messageBuilder.append(targetUserName).append(" 被降级为普通成员").toString();
        }
        try{
            groupMemberMapper.updateMemberRole(map);
        }catch (Exception e){
            throw new DatabaseException("数据库异常,禁言或者解禁失败,稍后再试");
        }

        groupNoticeMessageSender(sessionId, message);
    }

    @Override
    public void MuteOrCancelMuteGroupMember(long sessionId,long currentUserId,long targetUserId,String operationType) {

        /**
         * 先判断发起 禁言或者解禁请求 的用户是不是群里面的管理员或者群主角色
         * 如果不是,则返回false,表示 禁言或者解禁 失败
         * 如果是管理员或者群主发起的请求,则修改用户的Id在redis中memberId的值
         *
         * 1. 如果是禁言,则memberId的值设置为-1,表示这个member被禁言了
         * 2. 如果是解禁,则memberId的值设置为0,表示这个member被解禁了
         *
         * 然后发消息给全部群成员,让他们知道这个member被禁言了或者解禁了
         */

        String message=null;
        StringBuilder messageBuilder = new StringBuilder();
        String targetUserName=null;
        try{
            targetUserName = userMapper.selectUserNameById(targetUserId);
        }catch (Exception e){
            throw new DatabaseException("数据库异常,禁言或者解禁失败,稍后再试");
        }

        if(operationType.equals("mute member")){
            redisTemplate.opsForHash().put("Session_"+sessionId,String.valueOf(targetUserId),Long.valueOf(-1));
            message=messageBuilder.append(targetUserName).append(" 被管理员禁言了").toString();
        }else if(operationType.equals("cancel mute member")){
            redisTemplate.opsForHash().put("Session_"+sessionId,String.valueOf(targetUserId),Long.valueOf(0));
            message=messageBuilder.append(targetUserName).append(" 被管理员取消禁言了").toString();
        }

        groupNoticeMessageSender(sessionId, message);
    }

    private void groupNoticeMessageSender(long sessionId, String message) {
        Messages messages=new Messages();
        messages.setMessageId(SnowflakeIdUtil.messageIdWorker.nextId());
        messages.setSessionId(sessionId);
        //-1表示系统消息
        messages.setSenderId(-1);
        messages.setContent(message);
        messages.setMessageType("text");

        msgProducer.sendChatMessage(messages);
    }

    /**
     * 把群成员踢出群聊操作
     */
    @Override
    public void kickOutGroupMember(long sessionId, long targetUserId) {
        redisTemplate.opsForHash().delete("Session_"+sessionId,String.valueOf(targetUserId));
        try{
            groupMemberMapper.deleteGroupMember(sessionId,targetUserId);
        }catch (Exception e){
            throw new DatabaseException("数据库异常,踢出群成员失败,稍后再试");
        }
    }

    /**
     * 删除群聊操作必须延期执行,因为我的设计是,一些消息最开始是在redis中缓存,
     * redis定时会把这些暂时缓存的消息批量同步到mysql中,然后删除redis中的暂存的消息
     * 所以如果立马执行删除群聊操作,那么redis中的暂存的消息还没同步到mysql中,
     * 那么之后redis把一些群聊消息同步到数据库中后,这些消息相当于"无群认领",浪费内存
     * 所以我推迟执行删除群聊操作,在redis把这些消息同步到mysql后,再执行删除群聊操作
     * 推迟的时间大于redis把缓存消息同步到mysql的间隔时间,这里推迟2小时
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Override
    public void dismissGroup(long sessionId) {
        /**
         * 虽然删除群聊操作延期执行,但是删除群成员userId在redis中与该群id的关系可以立马执行
         * 第一是防止新的消息再发到群里,没有新的该群的消息在redis中等待同步到mysql,
         * 所以可以保证可以完完全全地删除该群的在数据库的所有消息
         * 第二redis删除该群的键值也很快
         */

        redisTemplate.delete("Session_"+sessionId);

        scheduler.schedule(() -> {

            /**
             * 1. 删除群聊
             * 2. 删除群聊中的所有成员
             * 3. 删除群聊中的所有消息
             * 4. 删除群聊中的所有redis中的数据
             */
            sessionMapper.deleteGroupSession(sessionId);
            groupMemberMapper.deleteAllGroupMembers(sessionId);
            messageMapper.deleteGroupMessages(sessionId);
        }, 2, TimeUnit.HOURS);
    }
    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    @Override
    @Transactional
    public void updateGroupInfo(Sessions sessions) {
        try {
            sessionMapper.updateGroupInfo(sessions);
        }catch (Exception e){
            throw new DatabaseException("数据库异常,更新群聊信息失败");
        }
    }
}
