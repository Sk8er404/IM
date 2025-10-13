package org.com.code.im.rocketMq.consumer;

import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.netty.nettyHandler.ChannelCrud;
import org.com.code.im.pojo.dto.CreateSessionOrInviteRequest;
import org.com.code.im.pojo.Messages;
import org.com.code.im.pojo.Sessions;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.rocketMq.producer.MsgProducer;
import org.com.code.im.service.session.SessionService;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(topic = "${rocketmq.topics.topic1}",
        consumerGroup = "${rocketmq.consumer.group2}",
        selectorExpression = "${rocketmq.tags.tag2}",
        messageModel = MessageModel.CLUSTERING,
        /**
         * 设置最大重试次数为0，表示不重试
        */
        maxReconsumeTimes=0)
public class GroupCreateConsumer implements RocketMQListener<String> {
    @Autowired
    private SessionService sessionService;
    @Autowired
    private MsgProducer msgProducer;
    @Autowired
    UserMapper userMapper;

    @Override
    public void onMessage(String sessionInfo) {
        CreateSessionOrInviteRequest createSessionOrInviteRequest = JSONObject.parseObject(sessionInfo, CreateSessionOrInviteRequest.class);

        Long ownerId = createSessionOrInviteRequest.getOwnerId();
        Long[] userIdArray = createSessionOrInviteRequest.getUserIds();
        Sessions session = createSessionOrInviteRequest.getSession();
        String requestType = createSessionOrInviteRequest.getRequestType();
        /**
         * 创建群聊,返回创建群聊的会话id
         */
        Long sessionId=null;
        if (requestType.equals("createGroup")) {
            try {
                sessionId = sessionService.createGroupChat(ownerId,userIdArray,session);
                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"创建群聊",null);
                ChannelCrud.sendMessage(ownerId,response.toJSONString());
            }catch (Exception e){
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"创建群聊",null);
                ChannelCrud.sendMessage(ownerId,response.toJSONString());
                throw new DatabaseException("创建群聊");
            }
        }else if (requestType.equals("inviteUsersToGroup")) {
            try {
                sessionId = sessionService.addGroupMember(session.getSessionId(),userIdArray);
                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"邀请用户入群聊",null);
                ChannelCrud.sendMessage(ownerId,response.toJSONString());
            }catch (Exception e){
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"邀请用户入群聊",null);
                ChannelCrud.sendMessage(ownerId,response.toJSONString());
                throw new DatabaseException("邀请用户入群聊");
            }
        }

        /**
         * 给所有的在该会话的成员发送一个消息,提醒他们已经加入这个会话
         */
        Messages messages = new Messages();

        messages.setSessionId(sessionId);

        // 设置消息的messageId,使用雪花算法生成
        messages.setMessageId(SnowflakeIdUtil.messageIdWorker.nextId());

        // 发送者的id如果为-1,则代表系统消息
        messages.setSenderId(-1);
        messages.setMessageType("text");
        if (requestType.equals("createGroup")) {
            messages.setContent("系统消息: 群聊创建成功，快来聊天吧");
        }else if (requestType.equals("inviteUsersToGroup")) {
            List<Long> userIds=new ArrayList<>(Arrays.asList(userIdArray));
            userIds.add(ownerId);

            String invitedUser=null,stringOwner=null;

            try {
                //如果查询成功，则使用用户名拼接字符串
                List<String> userNameList=userMapper.queryUserNameByManyIds(userIds);
                stringOwner=userNameList.get(userNameList.size()-1);
                userNameList.remove(userNameList.size()-1);

                invitedUser=userNameList.stream().map(String::valueOf).collect(Collectors.joining(","));
            }catch (Exception e){
                // 如果查询失败，使用 userIdArray 转换为字符串作为备选方案
                invitedUser = Arrays.toString(userIdArray);
                stringOwner = String.valueOf(ownerId);
                e.printStackTrace(); // 记录异常日志
            }
            StringBuilder sb = new StringBuilder();
            messages.setContent(sb.append("系统消息: ").append(stringOwner).append("邀请了").append(invitedUser).append("加入了群聊").toString());
        }

        //把系统的已经加入群的提醒消息发送到消息队列中
        msgProducer.sendChatMessage(messages);
    }
}
