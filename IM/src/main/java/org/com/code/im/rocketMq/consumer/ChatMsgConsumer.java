package org.com.code.im.rocketMq.consumer;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.com.code.im.netty.nettyHandler.ChannelCrud;
import org.com.code.im.pojo.Messages;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.session.MessageService;
import org.com.code.im.utils.BloomFilters;
import org.com.code.im.utils.TimeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RocketMQMessageListener(topic = "${rocketmq.topics.topic1}",
                        consumerGroup = "${rocketmq.consumer.group1}",
        selectorExpression = "${rocketmq.tags.tag1}",
        messageModel = MessageModel.CLUSTERING)
public class ChatMsgConsumer implements RocketMQListener<String> {

    @Qualifier("redisTemplateLong")
    @Autowired
    private RedisTemplate redisTemplate;

    @Qualifier("strRedisTemplate")
    @Autowired
    private RedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("messageImpl")
    private MessageService messagesService;

    @Override
    public void onMessage(String jsonMessage) {

        Messages message = JSONObject.parseObject(jsonMessage, Messages.class);
        /**
         * 所有群聊和私人会话的消息的创建时间都在ChatMsgConsumer这里创建
         */
        // 消息的创建毫秒级别的时间戳
        message.setTimestamp(System.currentTimeMillis());
        message.setCreatedAt(TimeConverter.getMilliTime());

        ResponseHandler responseHandler = new ResponseHandler(ResponseHandler.SUCCESS, "聊天消息", message);
        /**
         * 布隆过滤器去重，原本存在的消息一定会被判断为存在，但是有概率把原本不存在的消息误判为存在
         * 这里通过布隆过滤器通过消息id给消息去重
         */
        if(BloomFilters.checkIfDuplicatedMessage(String.valueOf(message.getMessageId()))){
            return;
        }

        /**
         * 每个消息都对应着一个sessionId,然后在redis中存储着以下数据
         * Session_sessionId member unreadMessageNumber
         * 所以当出现新消息的时候,先获取到这个消息的会话对应的所有成员,然后获取哪些成员在线,对于在线的成员直接发消息给他们
         * 对于不在线的成员,把他们的unreadMessageNumber+1,然后在redis存入以下的数据
         * userId messageId createdAt (服务器一收到消息的时候,就给消息设置的时间戳,精确到毫秒级别)
         * Zset按照时间排序 ，也方便客户端分页拉取离线信息
         * 接下来把所有消息存储到mysql中,等到他们上线后,查询redis,根据他们的userId从redis中获取到对应的消息Id,
         * 然后统一批量查询mysql数据库,发送给离线的用户,然后客户端确认接收后，返回最后一条消息的 messageId，服务端删除已读消息
         */

        /**
         * 获取到这个消息对应的会话的所有成员
         */
        Map<String,Long> memberOfSession = redisTemplate.opsForHash().entries("Session_"+message.getSessionId());
        //获取一个会话的全部成员allUserIds
        List<Long> allUserIds=new ArrayList<>();
        //如果是私聊则需要跳过private key值的遍历
        if(memberOfSession.get("private")!=null){
            memberOfSession.remove("private");
            for (String userId : memberOfSession.keySet()) {
                if(Long.parseLong(userId)==message.getSenderId())
                    continue;
                allUserIds.add(Long.parseLong(userId));
            }
            /**
             * 如果是群聊,则直接遍历,不需要担心有没有private这个key值,也就不用每一次遍历就判断一下是不是private
             * 因为之前一次就判断完有没有private了,节省性能
             */
        }else{
            memberOfSession.forEach((k,v)->{
                long id=Long.parseLong(k);
                allUserIds.add(id);
            });
        }

        /**
         * 假设每一个消息在redis中缓存n个小时，则recent_messages_集合存储
         * 这n小时内userId的全部消息的messageId和对应的messageTimestamp
         * 等到了n小时后，则删除recent_messages_userId集合，之后再有消息的时候，
         * 则重新存储recent_messages_userId集合
         * 
         * 这是为了历史消息的恢复，每当用户登录账号的时候，获取最早的未读消息的时间戳，
         * 然后获取这n个小时内，所有早于最早未读消息时间戳的缓存的消息，
         * 然后再查询mysql，获取存储在mysql中剩余的历史消息，然后就获取了所有历史消息
         * 
         * 当然，历史消息和未读消息是分开查询的，先查询未读消息，再查询最早未读消息前m天之内的历史消息
         * 
         */
        for(int i=0;i<allUserIds.size();i++){
            redisTemplate.opsForZSet().add("recent_messages_"+allUserIds.get(i),message.getMessageId(),message.getTimestamp());
            if(!redisTemplate.opsForSet().isMember("recent_message_member", allUserIds.get(i))){
                redisTemplate.opsForSet().add("recent_message_member", allUserIds.get(i));
            }
        }

        /**
         * 获取到该会话的在线成员的channel,发送消息
         */
        List<Long> onlineUserIds = getOnlineUserIds(allUserIds,"online_user");
        for (Long userId : onlineUserIds) {
            //如果是自己的channel,则跳过
            if(userId==message.getSenderId())
                continue;
            /**
             * 获取需要发送消息的用户id,获取他们账号的所有在线通道,然后发送消息
             * 一个账号可以在多台设备同时登录,故有多个channel,账号会被挤下线
             */
            List<Channel> channelList = ChannelCrud.getChannel(userId);
            if (channelList != null) {
                channelList.forEach(channel -> {
                    channel.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(responseHandler)));
                });
            }
        }

        /**
         * 获取到哪些成员不在线,然后按照
         * userId messageId createdAt
         * 把发送的未读消息id存储到redis的ZSet中，给用户相应的会话的unreadMessageNumber+1
         * 然后把完整的消息存储到mysql中,然后客户端上线后拉取离线消息的时候,
         * 用ZSet给未读的消息按照创建时间排序，获取这些未读的消息id
         * 然后先尝试在redis暂时缓存的最新消息中查找是否有这些消息,
         *
         * 1.如果有,直接发送给用户,当然也有可能,一部分未读消息已经同步到mysql数据库中了,
         *          另一部分还在redis暂时缓存的消息中,所以此时就要判断是否还需要继续访问数据库拉取剩余消息
         * 2.如果没有,再从mysql中批量获取
         *
         */
        List<Long> offlineUserIds = getOfflineUserIds(allUserIds,onlineUserIds);
        offlineUserIds.forEach(userId->{
            //新增该用户的未读消息,使用ZSet同时可以顺便统计所有的未读消息
            redisTemplate.opsForZSet().add("unread_message_"+userId,String.valueOf(message.getMessageId()),message.getTimestamp());
        });

        /**
         * 接下来就把这些消息暂时存储到redis中缓存
         * 每隔一段时间,把消息统一批量同步到mysql中,然后删除redis中的暂存的消息
         * 缓解mysql的压力
         */

        stringRedisTemplate.opsForHash().put("messages",String.valueOf(message.getMessageId()),JSONObject.toJSONString(message));
    }

    /** 每个用户上线,把他们的userId从添加到redis中的online_user
     *
     * 首先上面获取了某个会话id所对应的群聊或者单聊的全体成员的id信息
     * 然后此处使用 用redis pipeline批量查询这些成员哪些是在线
     * 在本地服务器获取通过哈希表获取在线人的的channel
     *
     * 存储online_user格式,hash形式
     * online_user userId userId
     */
    public List<Long> getOnlineUserIds(List<Long> userIds, String accountStatus){

        List<Long> OnlineUserIds=redisTemplate.executePipelined(new RedisCallback<Long>(){
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                byte[] key = redisTemplate.getStringSerializer().serialize(accountStatus);
                for (Object userId : userIds) {
                    byte[] userIdSerialized = redisTemplate.getStringSerializer().serialize(userId.toString());
                    connection.hGet(key, userIdSerialized);
                }
                return null;
            }
        });
        return OnlineUserIds.stream()
                .filter(Objects::nonNull)
                .toList();
    }
    public List<Long> getOfflineUserIds(List<Long> allUserIds,List<Long> onlineUserIds ){
        // 将 onlineUserIds 转换为 Set，以提高查找效率
        Set<Long> onlineUserSet = new HashSet<>(onlineUserIds);

        // 使用 Set 进行快速查找
        return allUserIds.stream()
                .filter(userId -> !onlineUserSet.contains(userId))
                .toList();
    }

    /**
     * 每隔一段时间,把redis的消息统一批量同步到mysql中,然后删除redis中的暂存的消息
     */
    @Scheduled(fixedRate = 3600000) // 每1个小时执行一次
    public void flushMessagesFromRedisToDataBase() {
        List<Messages> messagesList=new ArrayList<>();
        if (stringRedisTemplate.hasKey("messages")) {
            // 获取 Redis 中缓存储的全部离线消息列表
            Map<String, String> messagesMap = stringRedisTemplate.opsForHash().entries("messages");

            messagesMap.forEach((k,v)->{
                Messages messages=JSONObject.parseObject(v,Messages.class);
                messagesList.add(messages);
            });
            // 清空 Redis 中的消息列表
            stringRedisTemplate.delete("messages");
            //每个最近n小时的缓存的消息是不是都有对应的发送给谁userId
            //获取那些人的userId，才能把recent_messages_userId集合清空
            Set<Long> recentMessageMember = redisTemplate.opsForSet().members("recent_message_member");
            recentMessageMember.forEach(userId->{
                redisTemplate.opsForZSet().removeRange("recent_messages_"+userId,0,-1);
            });
            redisTemplate.delete("recent_message_member");

            // 批量插入消息到数据库
            messagesService.insertBatchMsg(messagesList);
        }
    }
}
