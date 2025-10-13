package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.Messages;

import java.util.List;

@Mapper
public interface MessageMapper {
    void insertBatchMsg(List<Messages> messages);
    List<Messages> queryUnreadMessages(List<Long> unreadMessageIds);
    int deleteGroupMessages(long sessionId);
    List<Messages> queryMessagesByTimestamp(List<Long> sessionIdList,long earliestMessageTimestamp,long minimumTimeStampScoreOfUnreadMessage);
}
