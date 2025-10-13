package org.com.code.im.netty.nettyHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelCrud {
    /**
     * 本地的服务器的上下文ConcurrentHashMap,同时保证线程安全
     * 同时由于是以ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>>的存储格式
     * 意味着同一个用户id在上下文可以对应多个channel，也就是同一个账号可以同时在不同设备以WebSocket连接在线，并同时接收消息
     *
     * 我设置成一个账号最多 MAX_ONLINE_NUMBER 设备同时在线,超出限制的,最早登录的账号会被挤下线
     */
    public static ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>> onlineUser = new ConcurrentHashMap<>();

    public static void addChannel(Long userId, ChannelHandlerContext ctx) {
        onlineUser.compute(userId, (key, channelList) -> {
            if (channelList == null) {
                channelList = new CopyOnWriteArrayList<>();
            }
            channelList.add(ctx.channel());
            return channelList;
        });
    }

    public static void removeChannel(Long userId, ChannelHandlerContext ctx) {
        onlineUser.computeIfPresent(userId, (key, channelList) -> {
            channelList.remove(ctx.channel());
            if (channelList.isEmpty()) {
                onlineUser.remove(key);
                return null;
            }
            return channelList;
        });
    }

    public static List<Channel> getChannel(Long userId) {
        return onlineUser.get(userId);
    }

    public static void sendMessage(Long userId, String message) {
        List<Channel> channelList = onlineUser.get(userId);
        if (channelList != null) {
            for (Channel ctx : channelList) {
                ctx.writeAndFlush(new TextWebSocketFrame(message));
            }
        }
    }
}
