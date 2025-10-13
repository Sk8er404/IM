package org.com.code.im.rocketMq.consumer;

import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = "chat",
        consumerGroup = "${rocketmq.consumer.group5}",
        selectorExpression = "session",
        messageModel = MessageModel.CLUSTERING)
public class CommentMsgConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String messages) {

    }
}
