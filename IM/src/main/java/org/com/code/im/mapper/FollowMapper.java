package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.Follows;
import org.com.code.im.pojo.UserFan;
import org.com.code.im.pojo.UserFollowing;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowMapper {
   public void insertFollow(Follows follows);
   public int cancelFollow(Follows follows);
   public Long selectUserFanMinAutoIncreaseId(long userId);
   public List<UserFan> queryFanListAndNavigateToNextPageByIdRange(Map map);
   public List<UserFan> queryFanListAndNavigateToPreviousPageByIdRange(Map map);
   List<UserFollowing> queryFollowList(Long userId);
}
