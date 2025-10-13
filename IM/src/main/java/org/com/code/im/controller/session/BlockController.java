package org.com.code.im.controller.session;

import org.com.code.im.exception.BadRequestException;
import org.com.code.im.pojo.Blocks;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.session.BlockService;
import org.com.code.im.utils.FriendManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 这里应用层的接口是同步返回的,web的普通http请求直接获取返回结果
 */

@RestController
public class BlockController {
    @Autowired
    BlockService blockService;
    @Qualifier("redisTemplateLong")
    @Autowired
    RedisTemplate redisTemplate;

    @PostMapping("/api/block/blockUser/{targetUserId}")
    public ResponseHandler blockUser(@PathVariable("targetUserId") long targetUserId) throws BadRequestException {

        long userId= Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        if(userId==targetUserId)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"不能屏蔽自己");

        if(FriendManager.areFriends(userId,targetUserId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"不能屏蔽好友");

        if(!redisTemplate.opsForSet().isMember("AllUserId",targetUserId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户不存在");

        if(redisTemplate.opsForSet().isMember("BlockedUserList_"+userId,targetUserId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户已被屏蔽");

        blockService.insertBlock(userId,targetUserId);

        return new ResponseHandler(ResponseHandler.SUCCESS,"屏蔽成功");
    }

    @DeleteMapping("/api/block/cancelBlock/{targetUserId}")
    public ResponseHandler cancelBlock(@PathVariable("targetUserId") long targetUserId) throws BadRequestException {

        long userId= Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        if(userId==targetUserId)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"不能取消屏蔽自己");

        if(!redisTemplate.opsForSet().isMember("AllUserId",targetUserId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户不存在");

        if(!redisTemplate.opsForSet().isMember("BlockedUserList_"+userId,targetUserId))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"该用户没有被屏蔽");

        blockService.cancelBlock(userId,targetUserId);

        return new ResponseHandler(ResponseHandler.SUCCESS,"取消屏蔽成功");
    }

    @GetMapping("/api/block/queryBlockedUserList")
    public ResponseHandler queryBlockedUserList() throws BadRequestException {
        long userId= Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Blocks> blockedUserList = blockService.queryBlockedUserList(userId);
        if(blockedUserList==null)
            return new ResponseHandler(ResponseHandler.SUCCESS,"黑名单上没有用户");
        return new ResponseHandler(ResponseHandler.SUCCESS,"查询成功",blockedUserList);
    }
}
