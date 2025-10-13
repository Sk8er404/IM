package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.User;
import org.com.code.im.pojo.UserNameAndAvatar;


import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    User selectUserById(long userId);

    String getAuth(long userId);

    int insertUser(User user);

    Long selectUserIdByName(String userName);

    Long updateUser(Map<String,Object> map);

    /**
     *  用于SpringSecurity的校验
     */
    User findUserByName(String userName);

    String selectUserNameById(long userId);

    List<String> queryUserNameByManyIds(List<Long> ids);

    String selectAvatarById(long userId);

    List<UserNameAndAvatar> selectNameAndAvatarByIds(List<Long> ids);

    List<User> selectUserByManyIds(List<Long> ids);
}

