package org.com.code.im.service.session.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.BlockMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.pojo.Blocks;
import org.com.code.im.service.session.BlockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BlockImpl implements BlockService {
    @Autowired
    private BlockMapper blockMapper;
    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public void insertBlock(long userId, long targetUserId) {
        try {
            redisTemplate.opsForSet().add("BlockedUserList_"+userId,targetUserId);
            String targetUserName=userMapper.selectUserNameById(targetUserId);
            Map map=new HashMap();
            map.put("blockerId",userId);
            map.put("blockedId",targetUserId);
            map.put("blockedName",targetUserName);
            blockMapper.insertBlock(map);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("数据库异常,屏蔽用户失败,稍后再试");
        }
    }

    @Override
    @Transactional
    public int cancelBlock(long userId, long targetUserId) {
        try {
            redisTemplate.opsForSet().remove("BlockedUserList_"+userId,targetUserId);
            Map map=new HashMap();
            map.put("blockerId",userId);
            map.put("blockedId",targetUserId);
            return blockMapper.cancelBlock(map);
        } catch (Exception e) {
            throw new DatabaseException("数据库异常,取消屏蔽失败,稍后再试");
        }
    }

    @Override
    @Transactional
    public List<Blocks> queryBlockedUserList(long userId) {
        try {
            return blockMapper.queryBlockedUserList(userId);
        } catch (Exception e) {
            throw new DatabaseException("数据库异常,查询屏蔽用户失败,稍后再试");
        }
    }
}
