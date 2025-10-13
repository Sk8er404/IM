package org.com.code.im.rocketMq.consumer;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.GroupMemberMapper;
import org.com.code.im.mapper.SessionMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.netty.nettyHandler.ChannelCrud;
import org.com.code.im.pojo.*;
import org.com.code.im.pojo.query.GroupMemberQuery;
import org.com.code.im.responseHandler.ResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RocketMQMessageListener(topic = "${rocketmq.topics.topic1}",
        consumerGroup = "${rocketmq.consumer.group4}",
        selectorExpression = "${rocketmq.tags.tag4}",
        messageModel = MessageModel.CLUSTERING,
        /**
         * 设置最大重试次数为0，表示不重试
        */
        maxReconsumeTimes=0)
public class querySessionConsumer implements RocketMQListener<String> {

    @Autowired
    private SessionMapper sessionMapper;
    @Autowired
    private GroupMemberMapper groupMemberMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public void onMessage(String message) {
        ConsumerMessageType messageType = JSON.parseObject(message, ConsumerMessageType.class);

        /**
         * 查询私人会话列表
         */
        if(messageType.getType().equals("privateSessionList")){
            try {

                List<PrivateMembers> sessionList=null;
                /**
                 * 先查询用户对应的私人对话的sessionId和每个sessionId对应的两个用户id,
                 * 即是userId1和userId2
                 * 但由于我始终根据用户id的大小排序插入privateMembers表格，
                 * 所有每两个用户之间不存在重复的两个session对话,只有一个session
                 */
                sessionList = sessionMapper.queryPrivateSessionList(messageType.getUserId());

                List<PrivateMemberQueryHandler> sessionListWithUser = new ArrayList<>(sessionList.size());
                List<Long> ids = new ArrayList<>(sessionList.size());

                /**
                 * 获取每个sessionId对应的用户id,比较userId1和userId2,哪个是用户自己的id哪个是对方的id
                 * 然后把用户和对方的这个对话的sessionId和对方id加入sessionListWithUser列表中
                 */
                Long userId;
                for (PrivateMembers session : sessionList) {
                    PrivateMemberQueryHandler sessionWithUser = new PrivateMemberQueryHandler();

                    //由于
                    if(session.getUserId1() == messageType.getUserId())
                        userId = session.getUserId2();
                    else
                        userId = session.getUserId1();

                    sessionWithUser.setUserId(userId);
                    sessionWithUser.setSessionId(session.getSessionId());

                    ids.add(userId);
                    sessionListWithUser.add(sessionWithUser);
                }

                /**
                 * 再根据sessionListWithUser列表里面的用户的id查询用户名，头像
                 */
                List<UserNameAndAvatar> userList = new ArrayList<>();
                // 检查ids列表是否为空，避免SQL语法错误
                if (!ids.isEmpty()) {
                    userList = userMapper.selectNameAndAvatarByIds(ids);
                }

                /**
                 * 将查询到的用户名，头像加入sessionListWithUser列表中
                 * 最终获取到了所有用户聊天的会话列表，包含和其他人聊天的sessionId，对方的用户id，对方名字，对方头像，
                 * 然后通过websocket连接返回给前端
                 */
                for (int i = 0; i < sessionListWithUser.size(); i++) {
                    sessionListWithUser.get(i).setUserName(userList.get(i).getUserName());
                    sessionListWithUser.get(i).setAvatar(userList.get(i).getAvatar());
                }

                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"查询私人会话列表",sessionListWithUser);
                ChannelCrud.sendMessage(messageType.getUserId(), response.toJSONString());

            }catch (Exception e){
                e.printStackTrace();
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"查询私人会话列表",null);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());
                throw new DatabaseException("查询私人会话列表");
            }

        }else if(messageType.getType().equals("groupSessionList")){
            try {

                List<Sessions> sessionList=null;
                sessionList = sessionMapper.queryGroupSessionList(messageType.getUserId());

                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"查询群聊列表",sessionList);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());

            }catch (Exception e){
                e.printStackTrace();
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"查询群聊列表",null);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());
                throw new DatabaseException("查询群聊列表");
            }

        }else if(messageType.getType().equals("groupMemberList")){
            try {
                List<GroupMemberQuery> sessionList=null;
                sessionList = groupMemberMapper.queryGroupMemberList(messageType.getSessionId());

                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"查询群成员列表",sessionList);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());

            }catch (Exception e){
                e.printStackTrace();
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"查询群成员列表",null);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());
                throw new DatabaseException("查询群成员列表");
            }
        }else if(messageType.getType().equals("groupInfo")){
            try {
                Sessions session=null;
                session = sessionMapper.queryGroupInfo(messageType.getSessionId());
                ResponseHandler response = new ResponseHandler(ResponseHandler.SUCCESS,"查询群信息",session);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());

            }catch (Exception e){
                e.printStackTrace();
                ResponseHandler response = new ResponseHandler(ResponseHandler.SERVER_ERROR,"查询群信息",null);
                ChannelCrud.sendMessage(messageType.getUserId(),response.toJSONString());
                throw new DatabaseException("查询群信息");
            }
        }
    }
}
