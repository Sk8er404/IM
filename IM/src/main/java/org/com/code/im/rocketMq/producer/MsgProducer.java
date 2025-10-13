package org.com.code.im.rocketMq.producer;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.com.code.im.exception.RocketmqException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
public class MsgProducer {
    @Autowired
    @Qualifier("CustomizedTemplate")
    private RocketMQTemplate producerTemplate;

    @Value("${rocketmq.topics.topic1}")
    private String topic1;
    
    @Value("${rocketmq.topics.topic2}")
    private String topic2;

    @Value("${rocketmq.tags.tag1}")
    private String tag1;
    
    @Value("${rocketmq.tags.tag2}")
    private String tag2;
    
    @Value("${rocketmq.tags.tag3}")
    private String tag3;
    
    @Value("${rocketmq.tags.tag4}")
    private String tag4;
    
    @Value("${rocketmq.tags.tag5}")
    private String tag5;

    /**
     * 发送聊天消息
     */
    public void sendChatMessage(Object content) {
        asyncSendMessage(content, topic1, tag1);
    }

    /**
     * 发送群创建消息
     */
    public void sendGroupCreateMessage(Object content) {
        asyncSendMessage(content, topic1, tag2);
    }

    /**
     * 发送私聊会话创建消息
     */
    public void sendPrivateSessionCreateMessage(Object content) {
        asyncSendMessage(content, topic1, tag3);
    }

    /**
     * 发送查询会话消息
     */
    public void sendQuerySessionMessage(Object content) {
        asyncSendMessage(content, topic1, tag4);
    }

    /**
     * 发送提醒消息
     */
    public void sendReminderMessage(Object content) {
        asyncSendMessage(content, topic2, tag5);
    }

    /**
     * 通用的异步发送消息方法
     */
    public void asyncSendMessage(Object content, String topic, String tag) {
        String msg = JSON.toJSONString(content);
        String destination = topic + ":" + tag;
        
        //自动实现 producer.setRetryTimesWhenSendFailed(3)最多重新发送3次 这个配置
        producerTemplate.asyncSend(destination, msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable throwable) {
                throw new RocketmqException("生产者发送消息失败,消息标签为:" + destination + ", 消息体为:" + msg);
            }
        });
    }
}
