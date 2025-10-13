package org.com.code.im.service.user;

import org.com.code.im.pojo.User;

import java.util.List;


public interface UserService {
    User selectUserById(Long userId);
    Long selectUserIdByNameAndPasswordAndReturnUserId(User login);
    void insertUser(User user);
    Long updateUser(User user);
    List<User> searchUserListByName(String userName, int page, int size);
}
