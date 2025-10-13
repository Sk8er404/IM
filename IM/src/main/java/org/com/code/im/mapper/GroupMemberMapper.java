package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.query.GroupMemberQuery;
import org.com.code.im.pojo.GroupMembers;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMemberMapper {
    public void insertMembersToGroup(List<GroupMembers> members);
    public List<GroupMemberQuery> queryGroupMemberList(long sessionId);
    public int deleteGroupMember(long sessionId, long userId);
    public int deleteAllGroupMembers(long sessionId);
    public int updateMemberRole(Map map);
}
