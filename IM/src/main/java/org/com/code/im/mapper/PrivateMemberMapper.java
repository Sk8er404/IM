package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.com.code.im.pojo.PrivateMembers;
import org.com.code.im.pojo.UserFollowing;

import java.util.List;
import java.util.Map;

@Mapper
public interface PrivateMemberMapper {
    void insertPrivateMember(PrivateMembers privateMember);
}
