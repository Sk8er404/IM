package org.com.code.im.controller.session;

import jakarta.servlet.http.HttpServletRequest;
import org.com.code.im.responseHandler.ResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 这里应用层的接口是同步返回的,web的普通http请求直接获取返回结果
 */

/**
 * 以下为前端发消息聊天的格式例子:
 *
 * {
 *     "sessionId":5455079943446528,
 *     "content":"hello",
 *     "messageType":"text",huoz
 *     "sequenceId":0
 * }
 *
 * 每一次发消息,前端必须让sequenceId自增1,否则后端不会接收这条消息,这是为了防止消息重复发送,
 * 每一次上线sequenceId从0开始,之后的任何一次对话,不管是群聊还是单聊,sequenceId都要在原来的基础上自增1
 * 直到账号下线后,再次上线,sequenceId才能再次从0开始.
 * 每一次前端发送的消息,后端都会返回消息确认这条消息是否成功发送,
 *
 * 这是成功的例子:
 * {
 *     "code":200, // 返回的状态码
 *     "data":null,
 *     "message": null,
 *     "sequenceId":1, // 下一次前端发送消息的sequenceId一定要大于这个数字
 * }
 * 这是失败的例子:
 * {
 *     "code": 400, // 返回的状态码
 *     "data": "sequenceId重复,发送失败\n", //各种消息发送失败的原因
 *     "message": "消息发送失败",  //总结表示消息发送失败了
 *     "sequenceId": 2 //下一次前端发送消息的sequenceId一定要大于这个数字
 * }
 *
 *
 * 无论是服务器返回的任何消息都是一同个格式,举个例子:
 * {
 *     "code":200,
 *     "data":null, //这是Object类型,所以有可能是数组,普通对象,基本数据类型等等
 *     "message": null,
 *     "sequenceId":1,
 * }
 */
@RestController
public class ChatController {

    @Value("${app.url}")
    private String url;
    @GetMapping("/api/chat")
    public ResponseHandler getWebSocketUrl(HttpServletRequest request) {
        String token = request.getHeader("token");
        return new ResponseHandler(ResponseHandler.SUCCESS, "前端访问该返回的URL码建立ws连接","ws://"+url+":8081/api/chat?token="+token);
    }
}
