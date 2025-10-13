package org.com.code.im.netty.nettyHandler;

import com.alibaba.fastjson2.JSON;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;


public class HeartBeatEventHandler extends ChannelDuplexHandler {
    /**
     * ChannelDuplexHandle是一个双向通道，既可以入站也可以出站
     *
     * 浏览器的客户端与netty服务器建立连接后，实现的是基于
     * WebSocket协议实现的长连接，所以客户端期望的是一个WebSocketFrame类型的消息，
     * 所以这里发送一个TextWebSocketFrame类型的消息，
     * 这样客户端才可以收到这个心跳消息
     */

    //利用创建好的心跳消息对象，避免频繁创建对象
    TextWebSocketFrame heartBeat = new TextWebSocketFrame();
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE ) {

                /**
                 * 发送心跳消息，并刷新缓冲区
                 * 每一次刷新缓冲区都会把heartbeat的ByteBuf内容情况,同时把heartbeat的引用次数减一
                 * 所以这里需要调用retain()方法，让heartbeat的引用次数加一,同时往它的BYteBuf中写内容，
                 */
                ReferenceCountUtil.retain(heartBeat);
                heartBeat.content().writeBytes(JSON.toJSONBytes("Ping"));
                ctx.channel().writeAndFlush(heartBeat);
            }else if(event.state() == IdleState.READER_IDLE){
                //如果读超时，则关闭连接
                ctx.channel().close();
            }
        }else{
            ctx.fireUserEventTriggered(evt);
        }
    }
}
