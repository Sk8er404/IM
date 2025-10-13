package org.com.code.im.netty.nettyHandler;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import org.com.code.im.mapper.MessageMapper;
import org.com.code.im.mapper.SessionMapper;
import org.com.code.im.netty.nettyServer.WebSocketChannelInitializer;
import org.com.code.im.pojo.LearningTask;
import org.com.code.im.pojo.Messages;
import org.com.code.im.responseHandler.ResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
public class OfflineMessageHandler extends SimpleChannelInboundHandler<String> {

    @Qualifier("strRedisTemplate")
    @Autowired
    RedisTemplate stringRedisTemplate;

    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    MessageMapper messageMapper;
    @Autowired
    SessionMapper sessionMapper;

    private int count=0;

    private long userId=0;

    private long unreadMessageNumber = 0;

    /**
     * 最早的未读消息的时间戳减去 下面这个变量 后获取的时间戳设为A
     * 则我会查询A时间戳之后和最早未读时间戳消息之前的所有的消息，用于消息历史的恢复
     * 然后我再从A时间戳开始，查询A时间戳之后的消息，用于未读消息的推送
     */
    //  15天
    private static final long earliestUnreadMessageTimestampMinusDays=1000*60*60*24*15;
    private long minimumTimeStampScoreOfUnreadMessage=System.currentTimeMillis();


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String str) throws Exception {

    }
    /**
     *用户一上线，在他尚未能向服务器发送消息之前，先从redis中获取未读消息，然后推送给用户
     *
     * 大坑!!!!
     * WebSocket 协议升级流程
     * HTTP 升级请求：客户端发送 HTTP 请求，请求将协议升级为 WebSocket。
     * 握手处理：WebSocketServerProtocolHandler 处理握手请求，并触发 HandshakeComplete 事件。
     * 协议切换：握手完成后，连接协议正式切换为 WebSocket，此时才能发送 TextWebSocketFrame 等 WebSocket 帧。
     * 2. 消息发送的时机要求
     * 必须在握手完成后发送消息：如果在握手完成前发送 TextWebSocketFrame，Netty 会认为当前协议仍为 HTTP，导致 UnsupportedOperationException。
     * 事件触发顺序：自定义事件（如用户上线通知）必须在 HandshakeComplete 事件之后触发。
     *
     * 所以这里我离线消息的推送,
     * 1. 我需要用户的userId,所以需要在用户上线时，获取到userBeOnlineAlarm自定义时间之后执行
     * 2. 如果过早发送离线消息,此时还没有升级成WebSocket协议,直接发送TextWebSocketFrame 会发送失效
     *
     * 因此我需要等到这两次事件都完成后,才能发送消息给用户,代码如下
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof WebSocketChannelInitializer.userBeOnlineAlarm) {
            count++;
            userId = (long) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
        }else if(evt instanceof WebSocketServerProtocolHandler.HandshakeComplete){
            count++;
        }
        if(count==2){
            List<Messages> messages =queryUnreadMessages(userId);

            /**
             * 1. 先发送用户的全部未读消息数量
             */
            ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.SUCCESS,"未读消息数量",unreadMessageNumber))));

            if(messages!=null){
                for (Messages message : messages) {
                    ResponseHandler responseMessage = new ResponseHandler(ResponseHandler.SUCCESS, "聊天消息", message);
                    ctx.channel().write(new TextWebSocketFrame(JSON.toJSONString(responseMessage)));
                }
                //统一刷新缓冲区，减小服务器压力
                ctx.channel().flush();
            }

            /**
             * 2. 再发送用户的最早未读消息前一段时间内的历史消息
             */
            ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.SUCCESS,"接收部分历史消息",null))));

            //最早未读消息的时间戳 - 15天 = 需要恢复历史消息的最早消息的时间戳
            long earliestMessageTimestamp = minimumTimeStampScoreOfUnreadMessage - earliestUnreadMessageTimestampMinusDays;

            //从earliestMessageTimestamp时间戳开始，查询earliestMessageTimestamp时间戳到minimumTimeStampScore时间戳之间的所有消息
            //当然因为这些历史消息有些在redis中，有些在数据库中，所以需要分开查询
            List<Messages> historicalMessages = queryHistoricalMessagesByTimestampRange(earliestMessageTimestamp);
            if(historicalMessages!=null){
                for (Messages message : historicalMessages) {
                    ResponseHandler responseMessage = new ResponseHandler(ResponseHandler.SUCCESS, "聊天消息", message);
                    ctx.channel().write(new TextWebSocketFrame(JSON.toJSONString(responseMessage)));
                }
                //统一刷新缓冲区，减小服务器压力
                ctx.channel().flush();
            }

            /**
             * 3. 再发送用户的设置的任务提醒消息
             */
            Set<String> taskRemindMessages = stringRedisTemplate.opsForSet().members("task_reminder_"+userId);
            List<LearningTask> taskRemindMessagesList = new ArrayList<>();
            for (String taskRemindMessage : taskRemindMessages) {
                LearningTask task = JSONObject.parseObject(taskRemindMessage,LearningTask.class);
                taskRemindMessagesList.add(task);
            }
            //删除task_reminder_userId集合
            stringRedisTemplate.delete("task_reminder_"+userId);
            
            // 根据reminderTime从早到晚排序（reminderTime不会为空）
            taskRemindMessagesList.sort((task1, task2) -> {
                return task1.getReminderTime().compareTo(task2.getReminderTime());
            });
            
            // 发送排序后的任务提醒消息列表
            for (LearningTask task : taskRemindMessagesList) {
                ResponseHandler responseHandler = new ResponseHandler(ResponseHandler.SUCCESS, "任务提醒消息", task);
                ChannelCrud.sendMessage(userId, JSON.toJSONString(responseHandler));
            }

            /**
             * 进行到这一步后offlineMessageHandler该做的都做了,可以移除掉了
             */
            ctx.fireUserEventTriggered(new WebSocketChannelInitializer.timeToRemoveOfflineMessageHandler());
        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    /**
     *
     *查询用户未读消息
     * 1. 先查询redis中的unread_message_userId,获取用户对应的unreadMessageId未读消息id集合
     * 如果没有未读消息,则返回空
     *
     * 2. 若有未读消息,则根据获取的unread_messageId集合从redis中的messages,即暂时缓存最新消息的集合，
     *    从中获取未读消息,如果获取完后，发现还有未读消息没有获取，说明那部分未读消息已经同步到数据库了
     *    所以此时需要查询数据库
     *    极端情况下，redis返回的未读消息id集合不为空，但是里面的未读消息元素都为空，此时也要查询数据库
     */
    public List<Messages> queryUnreadMessages(long userId){
        //
        String key = "unread_message_"+userId;
        //获取用户所有未读消息id的集合
        /**
         * range(...) 返回的是一个 有序的 Set（虽然 Set 通常无序）。
         * 实际上，Spring Data Redis 的 ZSetOperations.range(...) 返回的是 LinkedHashSet，它保留了插入顺序。
         * 所以 unreadMessageIds 是有序的，并且顺序是你从 ZSET 中按 score 升序取出来的顺序。
         */
        Set<String> unreadMessageIds = stringRedisTemplate.opsForZSet().range(key,0,-1);

        /**
         *获取全部未读消息id集合后,删除这个未读消息集合
         *
         * 同步删除（可能阻塞主线程）
         * stringRedisTemplate.delete(key);
         *
         * 异步删除（非阻塞）
         * stringRedisTemplate.unlink(key);
         */
        stringRedisTemplate.unlink(key);

        if(unreadMessageIds==null||unreadMessageIds.size()==0)
            return null;

        unreadMessageNumber = unreadMessageIds.size();

        //获取未读消息id的列表
        /**
         * 构造新的 ArrayList 时，使用的是 unreadMessageIds.iterator() 遍历的顺序。
         * 因为 unreadMessageIds 是 LinkedHashSet（或类似有序 Set），所以 iterator 的顺序保持了原始 ZSET 的排序。
         * 最终生成的 messageIds 列表仍然保持原始的排序。
         */
        List<String> messageIds = new ArrayList<>(unreadMessageIds);

        // 安全获取第一个消息的时间戳分数，防止空值异常
        Double scoreValue = redisTemplate.opsForZSet().score(key, messageIds.get(0));
        if (scoreValue != null) {
            minimumTimeStampScoreOfUnreadMessage = scoreValue.longValue();
        } else {
            // 如果获取不到分数，使用当前时间戳作为默认值
            minimumTimeStampScoreOfUnreadMessage = System.currentTimeMillis();
        }

        /**
         * 也有可能有redis缓存的部分消息已经同步到数据库了
         * 所以我先通过未读消息Id的集合，用管道批量查询redis获取未读的消息体,得到messageList
         */
        List<String> messageList =getMessageListFromRedis(messageIds);
        /**
         * 由于getMessageListFromRedis 方法:
         * 使用 executePipelined 方法批量查询消息。
         * 返回一个 List<String>，其中包含查询到的消息 JSON 字符串，如果某个 messageId 不存在，则对应位置为 null。
         *
         * 所以，如果 messageList 中某个位置为 null，则表示对应 messageId 不存在，需要从数据库中查询
         * 此时就把需要查询的messageId 加入到 unreadMessageIdInDatabase 集合中
         */
        List<Messages> messages = new ArrayList<>();
        List<Long> unreadMessageIdInDatabase = new ArrayList<>();
        for (int i = 0; i < messageList.size(); i++) {
            if(messageList.get(i)!=null){
                Messages message = JSON.parseObject(messageList.get(i), Messages.class);
                messages.add(message);
            }else{
                unreadMessageIdInDatabase.add(Long.parseLong(messageIds.get(i)));
            }
        }
        /**
         * 此时循环遍历后，获取了已经被同步到数据库的未读消息Id，unreadMessageIdInDatabase
         * 所以此时需要从数据库中批量查询未读消息
         *
         * 如果unreadMessageIdInDatabase集合为空，则说明redis缓存中获取的未读消息就是全部未读消息，此时直接返回未读消息即可
         */
        if(unreadMessageIdInDatabase.size()==0)
            return messages;
        List<Messages> messagesInDatabase = messageMapper.queryUnreadMessages(unreadMessageIdInDatabase);
        messages.addAll(messagesInDatabase);
        return messages;
    }

    /**
     *这个方法是拿一个人未读消息id的集合从暂时缓存到redis中的消息集合中拿数据
     *
     * messageIds: 未读消息id的集合
     * messageList: 未读消息的集合
     */
    public List<String> getMessageListFromRedis(List<String> messageIds){
        List<String> messageList = stringRedisTemplate.executePipelined(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                byte[] key = stringRedisTemplate.getStringSerializer().serialize("messages");
                for (String messageId : messageIds) {
                    byte[] messageIdSerialized = stringRedisTemplate.getStringSerializer().serialize(messageId);
                    connection.hGet(key, messageIdSerialized);
                }
                return null;
            }
        });
        return messageList;
    }


    //从earliestMessageTimestamp时间戳开始，查询earliestMessageTimestamp时间戳到minimumTimeStampScore时间戳之间的所有消息
    //当然因为这些历史消息有些在redis中，有些在数据库中，所以需要分开查询
    public List<Messages> queryHistoricalMessagesByTimestampRange(long earliestMessageTimestamp){
        //先从数据库中查询出用户参与的所有sessionId
        List<Long> sessionIdList = sessionMapper.queryAllSessionIdList(userId);
        //再根据sessionIdList从数据库中查询出earliestMessageTimestamp时间戳到minimumTimeStampScore时间戳之间的
        //所有对应不同sessionId的消息,并按时间戳从小到大排序
        List<Messages> historicalMessageListInDatabase = new ArrayList<>();
        if(sessionIdList!=null&&sessionIdList.size()!=0)
            historicalMessageListInDatabase = messageMapper.queryMessagesByTimestamp(sessionIdList,earliestMessageTimestamp,minimumTimeStampScoreOfUnreadMessage);

        /**
         * 假设每一个消息在redis中缓存n个小时，则recent_messages_userId集合存储
         * 这n小时内userId的全部消息的messageId和对应的messageTimestamp
         * 等到了n小时后，则删除recent_messages_userId集合及其对应的recent_message_member集合(记录userId的集合)，
         * 之后再有消息的时候，则重新存储recent_messages_userId集合和recent_message_member集合
         * 
         * 这是为了历史消息的恢复，每当用户登录账号的时候，获取最早的未读消息的时间戳，
         * 然后获取这n个小时内，所有早于最早未读消息时间戳的缓存的消息，
         * 然后再查询mysql，获取存储在mysql中剩余的历史消息，然后就获取了所有历史消息
         * 
         * 当然，历史消息和未读消息是分开查询的，先查询未读消息，再查询最早未读消息前m天之内的历史消息
         * 
         */
        List<Messages> recentMessageListInRedis = getHistoricalMessagesFromRedis(userId, earliestMessageTimestamp, minimumTimeStampScoreOfUnreadMessage);
        historicalMessageListInDatabase.addAll(recentMessageListInRedis);
        return historicalMessageListInDatabase;
    }

    /**
     * 这里必须使用lua脚本，因为先要从recent_messages_userId集合中获取这个用户的
     * 比最早未读消息时间戳早的，但是又还在redis缓存中的消息的messageId
     * 然后根据获取的messageId从redis中的messages集合中获取消息，最终获取到还在redis缓存中历史的消息
     * 
     * 但是这是两个步骤，如果刚刚完成从recent_messages_userId集合中获取messageId，
     * 然后因为定时任务的原因，接下来的messages集合中的消息被删除了，
     * 那就相当于卡住了
     * 
     * 所以必须使用lua脚本，一步完成两个操作，刚好redis是原子性的，所以在执行这个二并一的操作的时候，
     * 不会出现键值因为其他原因被突然删除的情况
     */
    public List<Messages> getHistoricalMessagesFromRedis(long userId, long earliestTimestamp, long latestTimestamp) {
        // 使用Lua脚本一步完成两个操作
        String script = 
            "local ids = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2]) " +
            "local messages = {} " +
            "for i, id in ipairs(ids) do " +
            "    local msg = redis.call('HGET', KEYS[2], id) " +
            "    if msg then " +
            "        table.insert(messages, msg) " +
            "    end " +
            "end " +
            "return messages";
        
        List<String> keys = Arrays.asList(
            "recent_messages_" + userId,
            "messages"
        );
        
        List<String> args = Arrays.asList(
            String.valueOf(earliestTimestamp),
            String.valueOf(latestTimestamp)
        );
        
        List<String> results = (List<String>) stringRedisTemplate.execute(
            new DefaultRedisScript<>(script, List.class), 
            keys, 
            args.toArray()
        );
        
        return results.stream()
                .map(jsonStr -> JSON.parseObject(jsonStr, Messages.class))
                .collect(Collectors.toList());
    }
}
