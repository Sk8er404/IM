package org.com.code.im.netty.nettyHandler;
import com.alibaba.fastjson.JSON;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import org.com.code.im.netty.nettyServer.WebSocketChannelInitializer;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.pojo.Messages;
import org.com.code.im.rocketMq.producer.MsgProducer;
import org.com.code.im.utils.DFAFilter;
import org.com.code.im.utils.FriendManager;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.com.code.im.utils.TimeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Scope("prototype")
public class MessagesHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /**
     *  RedisConfig用的是@Configuration注解,同时这里是用它的Bean方法,
     *  由于@Configuration注解,这个类是单例的,所以这个Bean方法得到的是同一个RedisTemplate实例
     *  所以不用担心高并发情况下反复创建和销毁对象造成性能损失
     */
    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    private MsgProducer msgProducer;

    private long userId=0;
    private String stringUserId =null;

    private long lastTime = 0;
    private long currentTime = 0;

    /**
     *  因为sequenceId只是为了保证某一段时间内客户端向服务器发送的消息的唯一性,
     *  不需要永远不变,只需要保存那一段连接时间的sequenceId,
     *  所以每次用户上线时,默认的用户消息的sequenceId为0
     *  然后客户端每次也默认用0作为起始的sequenceId
     *  之后客户端每次发送消息时,都会把sequenceId+1,
     *  然后服务器比较每一次消息的sequenceId看看有没有重复
     */
    //不同channel有自己的sequenceId用于消息去重
    private long sequenceId = -1;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        /**
         * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!11
         *  HttpServerCodec、HttpObjectAggregator, WebSocketAuthenticationHandler,
         *  和 自定义的WebSocketAuthenticationHandler,OfflineMessageHandler处理器
         *
         *  这些处理器的作用仅限于 WebSocket 握手阶段（即从 HTTP 请求升级到 WebSocket 连接的过程）。
         *  一旦握手完成,协议升级成WebSocket协议后，后续的消息通信将完全基于 WebSocket 协议，因此这些处理器不会再被调用。
         * 长期运行的处理器：如 WebSocketServerProtocolHandler、MessagesHandler 和 HeartBeatEventHandler，
         * 它们会一直存在于管道中，处理后续的所有 WebSocket 消息。
         *
         * 所以为了优化性能，当协议升级成功后,
         * 把HttpServerCodec、HttpObjectAggregator , WebSocketAuthenticationHandler
         * 和 自定义的WebSocketAuthenticationHandler,OfflineMessageHandler处理器 从管道中移除。
         */
         if(evt instanceof WebSocketChannelInitializer.timeToRemoveOfflineMessageHandler){
             // 获取用户id
             userId = (long) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
             stringUserId = String.valueOf(userId);

             /**
              * Netty 的 WebSocketServerProtocolHandler 在 WebSocket 握手完成后会自动进行以下操作：
              *
              * 移除 HttpServerCodec 和 HttpObjectAggregator 等 HTTP 相关处理器。
              * 插入 WebSocket 帧编解码器（如 WebSocketFrameEncoder 和 WebSocketFrameDecoder）。
              * 移除 内部的 WebSocketServerProtocolHandshakeHandler，因为它只在握手阶段有用。
              * 这意味着对于内置处理器（如 HttpServerCodec 和 HttpObjectAggregator），通常不需要手动移除。
              *
              * 所以我此处只移除我需要被移除的自定义处理器
              */
             ctx.pipeline().remove(WebSocketAuthenticationHandler.class);
             ctx.pipeline().remove(OfflineMessageHandler.class);
         }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        /**
         * 用户下线
         * 把用户id及其会话的channel从本地的服务器的上下文Map中删除
         * 删除redis中保存的在线用户id
         * 
         */
        Long userId = (Long) ctx.attr(AttributeKey.valueOf("userId")).get();
        if (userId == null) {
            // 如果userId为null，说明连接还没有完成认证就断开了，直接返回
            return;
        }
        
        // 从本地缓存中移除用户的channel
        ChannelCrud.removeChannel(userId, ctx);
        
        // 检查该用户是否还有其他活跃的channel
        List<Channel> ctxList = ChannelCrud.getChannel(userId);
        
        // 如果用户没有任何活跃channel，则从Redis中移除在线状态
        if(ctxList == null || ctxList.isEmpty()) {
            /**
             * 因为在执行如下代码块之前，如果网络异常或者用户强行断开连接，
             * 那么stringUserId字段可能还未被初始化,此时stringUserId字段为null，
             * 直接用stringUserId字段作为key删除redis中的在线用户状态会报错
             * 
             *  if(evt instanceof WebSocketChannelInitializer.timeToRemoveOfflineMessageHandler){
             *      // 获取用户id
             *      userId = (long) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
             *      stringUserId = String.valueOf(userId);
             *      ....
             *  }
             * 
             * 所以不能依赖stringUserId字段，因为stringUserId字段在连接异常断开时可能还未被初始化
             * 所以使用userId作为key
             * 
             *  */
            redisTemplate.opsForHash().delete("online_user", String.valueOf(userId));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        /**
         * 消息的创建时间的值可以有客户端本地创建,也可以选择服务器收到消息后创建
         * 我选择后者,因为客户端本地创建的时间可能不准确,
         * 但是服务器先收到的消息不一定是客户端先创建好的,也可能是后创建的消息先到服务器
         *
         * 用户需要发送的消息格式,举例
         * {
         *     "sequenceId":1,
         *     "sessionId":1,
         *     "content":"hello",
         *     "messageType":"text"
         * }
         */
        String jsonMessage = textWebSocketFrame.text();
        Messages messages=null;

        /**
         * 如果消息的格式不对，或者敏感词或者用户不存在某个对话里但他却仍然往这个对话里发消息，等等其他
         * 或者出现异常,被try catch捕获,则消息不合法
         */
        try{
            messages = JSON.parseObject(jsonMessage, Messages.class);
            /**
             * 限流操作：
             *
             * 设置一个currentTime和lastTime,
             * 如果当前时间与lastTime的差值大于1000毫秒,则接受此消息，则更新lastTime为当前时间
             * 如果小于1000毫秒，则不接收此消息，直接return
             */
            currentTime = System.currentTimeMillis();
            if(currentTime-lastTime>1000) {
                lastTime = currentTime;
            }else {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.BAD_REQUEST, "消息发送失败","请不要频繁发送消息",sequenceId))));
                return;
            }

            String messageError = checkMessage(messages);
            if(!messageError.isEmpty()){
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.BAD_REQUEST,"消息发送失败",messageError,sequenceId))));
                return;
            }
        }catch (Exception e){
            /**
             * 如果被try catch捕获到异常,一般是消息格式原因，则消息不合法
             */
            ctx.channel().write(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.BAD_REQUEST, "消息发送失败",  "未知错误",sequenceId))));
            return;
        }

        //如果消息合法,将当前channel的sequenceId更新为消息的sequenceId
        sequenceId = messages.getSequenceId();
        // 返回ResponseHandler,返回客户端 success和消息的sequenceId表示成功收到且合法
        ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(new ResponseHandler(ResponseHandler.SUCCESS,"OK",null,messages.getSequenceId()))));

        // 设置消息的messageId,使用雪花算法生成
        messages.setMessageId(SnowflakeIdUtil.messageIdWorker.nextId());
        //设置消息发送者的id
        messages.setSenderId((Long) ctx.channel().attr(AttributeKey.valueOf("userId")).get());
        //把用户发送的消息发送到消息队列中
        msgProducer.sendChatMessage(messages);
    }

    public String checkMessage(Messages messages){
        String messageError="";
        /**
         * sequenceId要求在本地发送的时候,每发送一次,该sequenceId自增1
         * 因此通过比较接收的消息的sequenceId和该channel中保存的sequenceId进行比较，
         * 如果接收的消息的sequenceId大于channel保存的sequenceId，则表示该消息是合法的，否则不合法
         * 从而达到消息去重
         *
         * 用户每次上线后,初始化channel,然后channel的初始化变量的sequenceId默认为-1,用户的客户端每次也可以默认从0开始发消息
         */
        if(!(sequenceId < messages.getSequenceId())){
            return messageError+="sequenceId重复,发送失败\n";
        }
        //判断用户是否存在与这个对话中,防止用户发消息发到一个他不在的群里或私人聊天对话中
        if(!redisTemplate.opsForHash().hasKey("Session_"+messages.getSessionId(), stringUserId)){
            return messageError+="用户不存在与这个对话中,发送失败\n";
        }
        if(checkIfBeingBlocked(messages.getSessionId())){
            return messageError+="你已被对方拉黑,发送失败\n";
        }

        //对消息进行审核
        messageReview(messages);

        return messageError;
        /**
         * 由于时间原因,前端暂时未完成关注模块,因此我暂时去除此非好友关系在对方回复你之前只能发送1条消息的机制
         */


//        /**
//         * 为了保护用户被骚扰，bilibili和抖音在互相关注，或对方回复之前只允许发送一条信息,这里的检查其实就是执行类似的逻辑
//         *
//         * 1. 如果对话双方没有互相关注对方:
//         *    假设 user1 和 user2 建立了最初对话,此时对话值为 0,表示user1和user2都可以发消息给对方,但只能发一条消息
//         *    如果user1发一条消息给user2,此时就会把user1的值改成-1,表示user1被禁言
//         *    如果user2回了一条消息给user1,此时会把user2的值改成-1,表示user2被禁言
//         *    如此循环,达到了对方回复之前只允许发送一条信息的功能
//         *
//         *  2.如果对方互相关注对方: 则双方可以无限发消息
//         */
//
//        //如果这是私人消息，则执行私人消息检查的逻辑
//        if(redisTemplate.opsForHash().hasKey("Session_"+messages.getSessionId(),"private")){
//            if(!checkIfFriendSendMsg(messages)){
//                return messageError+="你们还不是好友,在对方回复你之前你只能发送一条消息,发送失败\n";
//            }
//
//            //如果是群聊消息，则执行群聊消息检查的逻辑，看看发送这条消息的人是否被群管理员禁言
//        }else if(!checkIfNotBeingMuted(messages)){
//            return messageError+="你被群管理员禁言,发送失败\n";
//        }
//
//        return messageError;
    }


    public boolean checkIfFriendSendMsg(Messages messages) {
        //先获取私人对话的两位用户id
        Set<String> keys = redisTemplate.opsForHash().keys("Session_" + messages.getSessionId());
        List<Long> userIds = new ArrayList<>();
        //把两个用户id转成long类型
        for (String key : keys) {
            if (key.equals("private"))
                continue;
            userIds.add(Long.parseLong(key));
        }
        //把两个用户id放到位图中操作，查看他们是不是好友，即有没有互相关注
        //如果是好友，则返回true
        if (FriendManager.areFriends(userIds.get(0), userIds.get(1)))
            return true;
        /**
         * 如果不是好友，则查看 这条消息的发送者 在这个私人对话中的状态，如果是-1，
         * 就说明这个消息发送者在这个私人对话中已经发送一条消息，
         * 且对方还没有回回复，此时他不能再发消息了，被禁言，返回false
         */
        if ((long) redisTemplate.opsForHash().get("Session_" + messages.getSessionId(), stringUserId) == 0) {
            /**
             * 如果这条消息在私人对话状态为0，则表示他可以发消息给对方，
             * 也许这是初建私人对话的第一条消息，也许这是回复对方的消息
             * 在消息发送成功后，把该用户在私人对话中的状态改为-1，表示该用户已经发送过消息了，不能再发了
             * 然后把对方在私人对话中的状态改为0，表示对方可以发消息回复他
             * 最后返回true,让这条消息发送成功
             */
            for (Long userId : userIds) {
                String userIdStr = String.valueOf(userId);
                /**
                 * 这里的userIdStr.equals(stringUserId)判断很关键
                 */
                if (stringUserId.equals(userIdStr)) {
                    redisTemplate.opsForHash().put("Session_" + messages.getSessionId(), userIdStr, -1);
                    continue;
                }
                redisTemplate.opsForHash().put("Session_" + messages.getSessionId(), userIdStr, 0);
            }
            return true;
        }
        /**
         * 如果发送这条消息的人在redis中状态为-1，则表示该用户已经发送过消息了，被禁言了，返回false
         */
        return false;
    }

    public boolean checkIfNotBeingMuted(Messages messages){
        long sessionId=messages.getSessionId();
        long roleId=(long)redisTemplate.opsForHash().get("Session_"+sessionId,stringUserId);
        return roleId != -1;
    }

    public boolean checkIfBeingBlocked(long sessionId){
        if(redisTemplate.opsForHash().hasKey("Session_"+sessionId,"private")){
            Set<String> keys = redisTemplate.opsForHash().keys("Session_" + sessionId);
            keys.remove("private");
            keys.remove(stringUserId);
            String targetUserId=keys.iterator().next();
            if(redisTemplate.opsForSet().isMember("BlockedUserList_"+targetUserId,stringUserId))
                return true;
        }
        return false;
    }


    /**
     *对发来的消息进行审核
     */
    public void messageReview(Messages messages){
        if(messages.getMessageType().equals("text")){
            String filteredText = DFAFilter.filter(messages.getContent(),'*');
            messages.setContent(filteredText);
        }
    }
}
