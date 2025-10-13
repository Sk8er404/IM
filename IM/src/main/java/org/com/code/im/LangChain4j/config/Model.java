package org.com.code.im.LangChain4j.config;




import com.alibaba.dashscope.tokenizers.QwenTokenizer;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class Model {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.text-embeddingModel}")
    private String textEmbeddingModel;

    @Value("${spring.ai.dashscope.chatModel}")
    private String chatModel;

    public static int DimensionOfEmbeddingModel = 1024;


    // 将Tokenizer配置为单例Bean，它是线程安全的，可以被所有地方共享
    @Bean("tokenizer")
    public Tokenizer tokenizer() { // 假设您的Model类可以被注入
        return new QwenTokenizer();
    }

    @Bean("text-embedding-v3")
    public EmbeddingModel embeddingModel(){
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(textEmbeddingModel)
                .build();
    }

    @Bean("qwen-flash")
    public QwenChatModel qwenStreamingChatModel(){
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModel)
                .maxTokens(4096)
                //.enableSearch(true)
                .build();
    }

}