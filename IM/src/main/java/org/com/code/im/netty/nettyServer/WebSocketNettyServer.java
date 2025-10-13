package org.com.code.im.netty.nettyServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

@Component
public class WebSocketNettyServer {
    @Qualifier("redisTemplateLong")
    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${netty.port}")
    private int nettyPort;

    @Autowired
    WebSocketChannelInitializer webSocketChannelInitializer;

    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;

    /**
     *  阻塞主线程
     * 在 start() 方法中，以下代码会导致当前线程阻塞：
     *      future.channel().closeFuture().sync();
     *这行代码会阻塞当前线程，直到 Netty 服务器关闭。
     * 如果 @PostConstruct 方法运行在 Spring Boot 的主线程中（例如应用启动时的主线程），
     * 那么它会阻止 Spring Boot 完成其初始化过程，
     * 导致 Spring Boot 的 HTTP 服务（监听 8080 端口）无法正常启动。
     *
     * 所以此处要多加一个线程来运行Netty服务器，避免阻塞主线程，让主线程继续初始化Spring Boot的HTTP服务。
     */
    @PostConstruct
    public void startRunNettyServer() {
        new Thread(() -> {
            try {
                start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    public void start() throws Exception {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler())
                    .childHandler(webSocketChannelInitializer);

            ChannelFuture future = serverBootstrap.bind(nettyPort).sync();
            System.out.println("Server started on port " + nettyPort);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync(); // 设置超时时间为5秒
                }
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync(); // 设置超时时间为5秒
                }
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("Server Stopped");
            }
        }
    }

    /**
     * 每一次服务器重启后,相当于客户端全体掉线,所以每一次重启都要删除redis中在线用户的记录
     */
    @PostConstruct
    public void cleanOnlineUserTrace(){
        redisTemplate.delete("online_user");
    }
}
