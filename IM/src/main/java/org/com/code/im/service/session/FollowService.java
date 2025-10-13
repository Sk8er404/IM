package org.com.code.im.service.session;

import org.com.code.im.pojo.query.FanListPageQuery;
import org.com.code.im.pojo.dto.UserFanListResponse;
import org.com.code.im.pojo.UserFollowing;

import java.util.List;

public interface FollowService {
    //userId指用户要关注的人的id
    void insertFollow(long userId,long fanId);
    void cancelFollow(long userId,long fanId);
    UserFanListResponse queryFanList(FanListPageQuery fanListPageQuery);
    List<UserFollowing> queryFollowList(long userId);
    List<UserFollowing> queryFriendList(long userId);
}
