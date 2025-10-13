package org.com.code.im.service.session;

import org.com.code.im.pojo.Sessions;

public interface SessionService {
    Long createOrGetCurrentPrivateConversation(long ownerId,long userId);
    Long createGroupChat(long ownerId,Long[] userIds, Sessions sessions);
    Long addGroupMember(Long sessionId,Long[] userId);
    String queryGroupRole(long sessionId,long userId);
    void MuteOrCancelMuteGroupMember(long sessionId, long currentUserId, long targetUserId,String operationType);
    void dismissGroup(long sessionId);
    void kickOutGroupMember(long sessionId,long targetUserId);
    void updateGroupInfo(Sessions sessions);
    void updateMemberRole(long sessionId,long userId,String role);
}
