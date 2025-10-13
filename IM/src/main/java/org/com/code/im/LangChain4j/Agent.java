package org.com.code.im.LangChain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface Agent {
    /**
     *  @V（或直接用参数名）：用于模板变量（Prompt Variable）
     *  你想在 prompt 中动态插入变量，比如用户ID、时间、上下文等。
     *  示例：
     * java
     *
     * @UserMessage("用户ID是 {{userId}}，他说：{{message}}")
     * String chat(@V("userId") Long userId, @V("message") String message);
     *
     * @param messages
     * @return
     */

    @SystemMessage("""
        你是一个多源信息检索助手，可访问三类外部知识：
        1. **社区帖子库**：回答经验、教程、讨论类问题 → 使用 postSearchTools
        2. **视频描述库**：回答视频内容相关问题 → 使用 videoSearchTools
        3. **用户个人对话历史**：回答涉及用户偏好、过往聊天、记忆类问题 → 使用 chatHistorySearchTools

        请严格遵守：
        - 如果问题关于**用户自己说过什么、喜欢什么、历史行为**，且当前上下文无相关信息 → **必须调用 chatHistorySearchTools**，并提供 refinedQuestion 和 keyword。
        - 如果问题关于**通用知识、操作指南、他人经验** → 优先调用 postSearchTools。
        - 如果问题明确提到“视频”“看视频”“教程视频”等 → 调用 videoSearchTools。
        - 所有搜索查询必须语义完整、包含关键实体（如产品名、操作、日期等），避免模糊词。
        - **禁止编造答案**。若不确定信息来源，优先调用相应工具。
        """)
    @UserMessage("""
        以下是当前对话上下文（仅包含最近几轮）：
        {{messages}}

        请根据以上内容进行回复。
        """)
    String chat(@V("messages")List<ChatMessage> messages);

    @SystemMessage("""
        你是一个对话记忆摘要生成器，专门用于构建长期对话记忆库。请对以下单轮用户与AI的对话（仅包含一次用户提问和AI回复）生成一个检索优化的摘要。

        要求：
        1. **聚焦关键信息**：提取用户的核心意图、明确请求、涉及的实体（如人名、产品名、订单号、日期、地点等）以及AI给出的关键回应或动作。
        2. **独立可检索**：摘要必须自包含，不依赖外部上下文，避免使用“他”“它”“这个”等代词。
        3. **语言风格**：使用客观、简洁的陈述句，避免寒暄、情感表达或模糊词汇（如“可能”“大概”）。
        4. **搜索友好**：
         - 包含显式关键词（如“取消订单”“查询物流”“设置提醒”）；
        - 语义完整，适合向量相似度匹配。
        5. **严格限制**：输出为一段连贯文字，不超过200字，不包含任何额外说明、标题或格式符号。
        """)
    @UserMessage("""
请总结以下对话内容：
{{messages}}
""")
    String summaryConversation(List<ChatMessage> messages);
}
