package org.com.code.im.LangChain4j.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.com.code.im.exception.AIModelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {
    @Autowired
    @Qualifier("text-embedding-v3")
    private EmbeddingModel embeddingModel;

    /**
     * 单文本向量化方法
     * @param text
     * @return
     */
    public float[] getEmbedding(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        if (response != null && response.content() != null) {
            return response.content().vector();
        }
        throw new AIModelException("获取Embedding失败");
    }

    /**
     * 批量文本向量化方法
     * @param texts 需要向量化的文本文档列表
     * @return 向量列表，顺序与输入文本一致
     */
    public List<float[]> getEmbeddings(List<String> texts) {
        // embedAll方法专为批量处理设计，它会一次性将所有文本发送到模型API
        // 将字符串列表转换为TextSegment列表
        List<TextSegment> textSegments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        
        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);
        if (response != null && response.content() != null) {
            return response.content().stream()
                    .map(Embedding::vector)
                    .collect(Collectors.toList());
        }
        throw new AIModelException("批量获取Embedding失败");
    }
}