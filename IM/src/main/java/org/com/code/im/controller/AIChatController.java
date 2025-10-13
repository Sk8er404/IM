package org.com.code.im.controller;

import dev.langchain4j.data.message.UserMessage;
import org.com.code.im.LangChain4j.Service.ChatMemoryService;
import org.com.code.im.LangChain4j.Service.ChatModelService;
import org.com.code.im.LangChain4j.dto.ChatConversation;
import org.com.code.im.pojo.query.AIChatQuery;
import org.com.code.im.responseHandler.ResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class AIChatController {
    @Autowired
    ChatMemoryService chatMemoryService;

    @Autowired
    ChatModelService chatModelService;


    /**
     * 分页获取用户窗口的消息列表（按时间顺序，从旧到新），页大小为 5
     * @param startPage 从1开始
     * @return
     */

    @GetMapping("/api/ai/getHistoryMessage")
    public ResponseHandler getHistoryMessage(@RequestParam int startPage){
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        /**
         * chatConversationList 包含的每一个chatWindow里面的对话顺序是： 用户问题->AI回答->用户问题->AI回答->...
         */
        if(startPage < 1){
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"页数不能小于1");
        }
        List<ChatConversation> chatConversationList = chatMemoryService.getConversationHistory(userId,startPage);

        /**
         * {
         * "code": 200,
         * "message": "没有任何历史消息,请创建一个新的聊天对话",
         * "data": null,
         * "sequenceId": null
         * }
         */
        if(chatConversationList.isEmpty()){
            return new ResponseHandler(ResponseHandler.SUCCESS,"没有任何历史消息,请创建一个新的聊天对话");
        }
        return new ResponseHandler(ResponseHandler.SUCCESS,"获取所有的历史消息",chatConversationList);
    }

    @PostMapping("/api/ai/createNewConversation")
    public ResponseHandler createNewConversation(){
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        String newConversationId = chatMemoryService.createNewConversation(userId);
        return new ResponseHandler(ResponseHandler.SUCCESS,"创建新对话成功",newConversationId);
    }

    @DeleteMapping("/api/ai/deleteConversation")
    public ResponseHandler deleteConversation(@RequestParam("conversationId") String conversationId){
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        chatMemoryService.deleteConversation(userId,conversationId);
        return new ResponseHandler(ResponseHandler.SUCCESS,"删除对话窗口成功",null);
    }

    /**
     * @param aiChatQuery
     * @return
     * @throws IOException
     */
    @PostMapping(path = "/api/ai/sendMessage")
    public String sendMessage(@RequestBody AIChatQuery aiChatQuery) throws IOException {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        String userQuestion = aiChatQuery.getUserQuestion();
        UserMessage userMessage = new UserMessage(userQuestion);

        return chatModelService.sendMessage(userId,aiChatQuery.getConversationId(),userMessage);
        /**
         * 在 LangChain 框架中，虽然你可以手动创建 ToolExecutionResultMessage（工具执行结果消息）并自定义其 id 和 toolName，
         * 但将这种手动调用的结果与由 AI Agent（如 Assistant）自动发起的工具调用流程混合在一起是不可取的。
         * 从技术上讲，这不会导致直接的程序冲突或崩溃。因为 AI 自动生成的工具调用 id 是高度随机且唯一的，几乎不可能与你手动设置的 id 相同。
         * 然而，真正的问题在于逻辑层面。AI AgentService 的工作模式严格遵循一个“请求-响应”的闭环：
         * AI 发出一个带有特定 id 的 ToolExecutionRequest（请求），并期望收到一个带有完全相同 id 的 ToolExecutionResultMessage（结果）作为回应。这个配对关系是其推理链条的关键一环。
         * 当你手动将一个结果消息塞入对话历史时，对于 AI 来说，这是一个没有对应请求的“凭空出现”的答案。这会打破它严谨的逻辑流程，导致 AI 感到困惑。这种困惑可能引发多种不良后果，
         * 例如 AI 忽略你提供的信息，或者其推理过程被打断，从而产生不相关或错误的回答。
         * 因此，最佳实践是：如果你想让 AI 利用你手动获取的信息，不应该直接创建 ToolExecutionResultMessage。
         * 更清晰、更可靠的做法是，将这些信息作为普通用户消息（UserMessage）的一部分明确地告知 AI。例如，你可以这样说：“我已经查到订单状态是‘已发货’，请根据这个信息帮我写一封邮件。” 这样做可以保持对话的逻辑清晰，让 AI 在有明确上下文的情况下，准确地理解和执行你的指令。
         */
    }
}