package org.com.code.im.rocketMq.consumer;

import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.netty.nettyHandler.ChannelCrud;
import org.com.code.im.pojo.dto.CreateSessionOrInviteRequest;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@RocketMQMessageListener(topic = "${rocketmq.topics.topic1}",
        consumerGroup = "${rocketmq.consumer.group3}",
        selectorExpression = "${rocketmq.tags.tag3}",
        messageModel = MessageModel.CLUSTERING,
        /**
         * 设置最大重试次数为0，表示不重试
        */
        maxReconsumeTimes=0)
public class PrivateSessionCreateConsumer implements RocketMQListener<String> {
    @Autowired
    private SessionService sessionService;

    @Override
    public void onMessage(String sessionInfo) {
        CreateSessionOrInviteRequest request = JSONObject.parseObject(sessionInfo, CreateSessionOrInviteRequest.class);
        long ownerId  =request.getOwnerId();
        long targetId = request.getUserIds()[0];

        try{
            Long sessionId = sessionService.createOrGetCurrentPrivateConversation(ownerId,targetId);
            ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"创建或查找私人会话",sessionId);
            ChannelCrud.sendMessage(ownerId,response.toJSONString());
        }catch (Exception e){
            e.printStackTrace();
            ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"创建或查找私人会话",null);
            ChannelCrud.sendMessage(ownerId,response.toJSONString());
            throw new DatabaseException("创建或查找私人会话");
        }
    }
}
