package org.com.code.im.LangChain4j.Service;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import org.com.code.im.LangChain4j.Agent;
import org.com.code.im.LangChain4j.tool.SearchTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentService {

    @Autowired
    @Qualifier("qwen-flash")
    QwenChatModel qwenChatModel;

    @Autowired
    SearchTool searchTool;

    @Bean("ToolAgent")
    public Agent createAgent(){
        return AiServices.builder(Agent.class)
                .chatLanguageModel(qwenChatModel)
                .tools(searchTool).build();
    }

    @Bean("ConversationSummaryAgent")
    public Agent createConversationSummaryAgent(){
        return AiServices.builder(Agent.class)
                .chatLanguageModel(qwenChatModel)
                .build();
    }
}
