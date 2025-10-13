package org.com.code.im.config;

import org.com.code.im.pojo.User;
import org.com.code.im.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;


@Component
public class DBUserDetailsManager implements UserDetailsManager, UserDetailsPasswordService {

    @Autowired
    private UserMapper userMapper;
    @Qualifier("redisTemplateLong")
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        User user = userMapper.findUserByName(userName);
        if (user == null) {
            throw new UsernameNotFoundException("用户或密码错误");
        }else{
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            for(String authority:user.getAuth().split(" ")){
                authorities.add(new SimpleGrantedAuthority(authority));
            }
            return new org.springframework.security.core.userdetails.User(
                    user.getUserName(),
                    user.getPassword(),
                    true,
                    true, //用户账号是否没过期
                    true, //用户凭证是否过期
                    user.getLocked() == 0, //用户是否未被锁定,为0表示未被锁定
                    authorities); //权限列表
        }
    }

    public void myCreateUser(User user) {
        redisTemplate.opsForSet().add("AllUserId", user.getId());
        //管理员账号信息手动插入数据库创建，普通用户则在普通登录接口创建
        userMapper.insertUser(user);
    }

    @Override
    public void createUser(UserDetails userDetails) {

    }

    @Override
    public void updateUser(UserDetails user) {

    }

    @Override
    public void deleteUser(String username) {

    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {

    }

    @Override
    public boolean userExists(String username) {
        return false;
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        return null;
    }
}
