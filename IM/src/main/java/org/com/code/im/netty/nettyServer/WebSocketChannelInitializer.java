package org.com.code.im.netty.nettyServer;

import io.netty.channel.*;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.com.code.im.netty.nettyHandler.HeartBeatEventHandler;
import org.com.code.im.netty.nettyHandler.OfflineMessageHandler;
import org.com.code.im.netty.nettyHandler.WebSocketAuthenticationHandler;
import org.com.code.im.netty.nettyHandler.MessagesHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketChannelInitializer extends ChannelInitializer {
    @Value("${netty.path}")
    private String nettyPath;


    /**
     * handler不能共享用同一个单例,需要为不同的channel创建不同的实例
     * 所以把handler标记为@Scope("prototype"),然后通过
     * applicationContext.getBean(handler.class)手动获取到不同的实例
     * 之所以不用@Autowired注入,而是通过手动获取是因为
     * WebSocketAuthenticationHandler是单例的
     *
     * 当你将某个Bean定义为@Scope("prototype")，理论上每次请求该Bean时都会获得一个新的实例。
     * 然而，如果这个原型作用域的Bean被注入到一个单例Bean中（例如通过@Autowired），
     * 那么实际上只会创建一次该原型Bean，并且之后所有对该Bean的引用都将指向这个初始实例。
     * 这是因为单例Bean本身只初始化一次，所以其依赖的所有其他Bean（包括标记为prototype的）
     * 也只会在第一次创建时被注入。
     *
     */
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpServerCodec())
                // 支持大数据流
                .addLast(new ChunkedWriteHandler())
                //一次接收的请求的数据包的最大值,8mb
                .addLast(new HttpObjectAggregator(8192))
                //对http请求进行token认证，识别用户身份，如果认证成功，才能往下升级成WebSocket连接
                //由于我需要提取请求头的token，所以我这个自定义的认证处理器需要继承放在HttpObjectAggregator后面，
                //HttpObjectAggregator会将Http解码后的数据包聚合成一个完整的HttpRequest和HttpContent，这是Http防止半包粘包的必要措施
                .addLast(applicationContext.getBean(WebSocketAuthenticationHandler.class))

                .addLast(new IdleStateHandler( 0, 0,0, TimeUnit.SECONDS))
                //处理心跳检测的包
                .addLast(new HeartBeatEventHandler())
                /**
                 * 浏览器的 WebSocket API 是唯一标准化的全双工通信协议，所有现代浏览器（Chrome/Firefox/Safari/Edge）
                 * 均强制使用 WebSocket 协议进行长连接通信，无法直接使用自定义二进制协议。
                 */
                //入站: 检查是否是 WebSocket 握手请求（通过 `Upgrade` 头部判断）。
                //      如果是 WebSocket 握手请求，则完成协议升级，后续消息会被解码为
                //      WebSocket帧（如 `TextWebSocketFrame` 或 `BinaryWebSocketFrame`）。
                //出站:将WebSocketFrame将消息封装为 WebSocket 协议格式。
                .addLast(new WebSocketServerProtocolHandler(nettyPath))
                /**
                 * 任何用TextWebSocketFrame或BinaryWebSocketFrame发送给客户端的消息，
                 * 都必须在WebSocketServerProtocolHandler后面,只有这样消息被发送的时候
                 * 才会被正确处理成WebSocket协议格式的字节流,然后发送给客户端，否则会被丢弃。
                 */

                //推送离线消息
                .addLast(applicationContext.getBean(OfflineMessageHandler.class))

                //处理WebSocketFrame
                .addLast(applicationContext.getBean(MessagesHandler.class));
    }

    public static class userBeOnlineAlarm {
    }
    public static class timeToRemoveOfflineMessageHandler {
    }
}
