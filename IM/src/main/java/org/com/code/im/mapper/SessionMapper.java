package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.PrivateMembers;
import org.com.code.im.pojo.Sessions;

import java.util.List;

@Mapper
public interface SessionMapper {
    public void createSession(Sessions sessions);
    public Long selectPrivateSession(long userId1, long userId2);
    public Sessions queryGroupInfo(long sessionId);
    public List<Sessions> queryGroupSessionList(long userId);
    public List<PrivateMembers> queryPrivateSessionList(long userId);
    public int deleteGroupSession(long sessionId);
    public int updateGroupInfo(Sessions sessions);
    public List<Long> queryGroupSessionIdList(long userId);
    public List<Long> queryAllSessionIdList(long userId);
}
