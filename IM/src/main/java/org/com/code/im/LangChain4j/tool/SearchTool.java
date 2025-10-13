package org.com.code.im.LangChain4j.tool;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.Service.EmbeddingService;
import org.com.code.im.LangChain4j.dto.ChatSession;
import org.com.code.im.pojo.enums.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class SearchTool {

    @Autowired
    ElasticUtil elasticUtil;

    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;

    @Autowired
    EmbeddingService embeddingService;

    public static final float ChatContentSimilarity = 0.8f;
    public static final int NumberOfCalledChatDocuments = 15;
    public static final int NumberOfRemainedChatDocuments = 5;

    public static final float textWeight = 0.4f;
    public static final float knnWeight = 0.6f;

    /**
     * 参数名 refinedQuestion 在这里非常重要，它和 @Tool 注解里的描述一样，都是为了帮助 AI 理解这个工具以及如何正确地使用它。
     *
     * @Tool 的工作原理
     * 当 LangChain4j 框架看到 @Tool 标记一个方法时，它会在背后做这样一件事：它会把这个 Java 方法转换成一个结构化的“工具说明书”，然后把这份说明书发给大语言模型（LLM）。
     * 对于你的代码：
     *
     * Java
     *
     * @Tool("一个搜索与问题相关帖子的工具")
     * public String postSearchTools(String refinedQuestion)
     * LangChain4j 会生成一份类似下面这样的“说明书”（通常是 JSON 格式）：
     *
     * JSON
     *
     * {
     *   "tool_name": "postSearchTools",
     *   "description": "一个搜索与问题相关帖子的工具",
     *   "parameters": {
     *     "type": "object",
     *     "properties": {
     *       "refinedQuestion": {
     *         "type": "string",
     *         "description": "（这里会自动生成一个描述，通常就是参数名本身）"
     *       }
     *     },
     *     "required": ["refinedQuestion"]
     *   }
     * }
     * AI 是如何“阅读”这份说明书的：
     * 我有什么工具？
     * 工具名 (tool_name): postSearchTools。AI 知道如果要调用这个工具，就要用这个名字。
     *
     * 工具是干嘛的？
     * (description): "一个搜索与问题相关帖子的工具"。这是最重要的部分！AI 根据这个描述来判断在什么情况下应该使用这个工具。
     *
     * 这个工具怎么用？
     *
     * 需要什么参数？ (parameters): 它看到需要一个叫 refinedQuestion 的参数。
     *
     * 这个参数是什么？: 参数的名字 refinedQuestion（意为“优化过的问题”）给了 AI 一个强烈的暗示：传给我的不应该是用户原始的、口语化的问题，而应该是一个更精炼、更适合搜索的查询。
     *
     * 所以，一个具有描述性的参数名，比如 refinedQuestion，远比一个模糊的名字（如 input 或 s）要好得多。它能帮助 AI 更准确地决定应该传入什么样的值。
     *
     * ## 如何做得更好：使用 @P 注解
     * 为了让 AI 更清晰地理解每个参数的含义，LangChain4j 提供了一个 @P 注解，让你能为参数添加更详细的描述。
     *
     * 这就像是给“说明书”里的参数部分添加了更详细的注释。
     *
     * 优化后的代码：
     *
     * Java
     *
     * @Tool("一个搜索与问题相关帖子的工具")
     * public String postSearchTools(
     *         @P("经过大模型优化后的、适合作为搜索引擎关键词的核心问题") String refinedQuestion
     * ) throws IOException {
     *     // ...
     * }
     * 现在，AI 看到的“说明书”会变成这样：
     *
     * JSON
     *
     * {
     *   // ...
     *   "properties": {
     *     "refinedQuestion": {
     *       "type": "string",
     *       "description": "经过大模型优化后的、适合作为搜索引擎关键词的核心问题" // <-- 描述变得非常清晰！
     *     }
     *   }
     *   // ...
     * }
     * 这样一来，AI 就能万无一失地理解 refinedQuestion 参数到底需要什么样的数据，从而大大提高它正确调用工具的成功率。
     *
     * @param refinedQuestion
     * @return
     * @throws IOException
     */
    @Tool("当用户询问与社区帖子相关的问题（如经验分享、教程、讨论等），请使用此工具搜索相关帖子内容。输入应为一个语义完整、适合关键词和向量搜索的查询语句。")
    public String postSearchTools(@P("语义清晰、包含关键实体、适合混合搜索（关键词+向量）的查询句子")
                                      String refinedQuestion) throws IOException {
        List<Map<String,Object>> postChunkList = elasticUtil.ragHybridSearch(refinedQuestion, ContentType.POST, Arrays.asList("title","content","tags"));

        System.out.println(refinedQuestion);
        List<String> reference = new ArrayList<>();

        reference.add("以下是查找到的可能相关的帖子文本块：\n");
        for (Map<String, Object> postChunk : postChunkList) {
            String postChunkText = "title:"+postChunk.get("title").toString()+"\ncontent"+postChunk.get("content").toString();
            reference.add(postChunkText);
        }
        return reference.toString();
    }


    @Tool("当用户询问与视频内容相关的问题（如教学视频、产品演示、讲解等），请使用此工具搜索视频的文本描述。输入应为一个语义完整、适合混合搜索的查询语句。")
    public String videoSearchTools(@P("语义清晰、包含关键实体、适合混合搜索（关键词+向量）的查询句子'")
                                      String refinedQuestion) throws IOException {
        List<Map<String,Object>> videoChunkList = elasticUtil.ragHybridSearch(refinedQuestion, ContentType.VIDEO, Arrays.asList("title","description"));
        List<String> reference = new ArrayList<>();

        reference.add("以下是查找到的可能相关的视频文本块：\n");
        for (Map<String, Object> videoChunk : videoChunkList) {
            String videoChunkText = "title:"+videoChunk.get("title").toString()+"\ndescription:"+videoChunk.get("description").toString();
            reference.add(videoChunkText);
        }
        return reference.toString();
    }

    @Tool("当用户的问题涉及个人偏好、过往对话、历史聊天记录或记忆类内容（如'我喜欢谁'、'上次我们聊了什么'、'我之前提过XX吗'），且当前上下文中没有相关信息时，必须调用此工具。")
    public List<Map<String,String>> chatHistorySearchTools(
            @P("将用户问题改写为语义完整、适合向量搜索的句子")   String refinedQuestion,
            @P("将用户原本的问题经过大模型优化后的、适合作为搜索引擎关键词搜索的句子") String keyword
    ) throws IOException {

        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());

        float[] queryVectorArray = embeddingService.getEmbedding(refinedQuestion);
        List<Float> queryVector = new ArrayList<>();
        for (float f : queryVectorArray) {
            queryVector.add(f);
        }
        // 执行向量搜索 (KNN Search)
        SearchRequest knnSearchRequest = SearchRequest.of(s -> s
                .index(ElasticConfig.USER_AI_DIALOGUE_MEMORY_INDEX)
                .knn(k -> k
                        .field("summary_embedding")
                        .queryVector(queryVector)
                        .k(NumberOfCalledChatDocuments)
                        .numCandidates(150)
                        .similarity(ChatContentSimilarity)

                        // 布尔过滤器先把不属于当前用户的对话记录过滤掉，再进行向量近似搜索
                        .filter(Query.of(q -> q
                                .term(t -> t
                                        .field("user_id")
                                        .value(userId)
                                )))
                        // 只返回指定字段，减小内存和网络开销
                ).source(SourceConfig.of(sc ->
                                sc.filter(i ->i.includes(List.of("original_question","original_answer"))))

                /**
                 * knn.k 决定了最终返回的 KNN 结果数量，而 size 在纯 KNN 搜索中通常被忽略或仅作为辅助限制。
                 * 但如果你同时使用了 query（全文检索）和 knn，那么 size 会影响整体结果数量,比如在如下场景
                 * 混合搜索（同时有 .query() 和 .knn()）
                 *
                 * ```java
                 * SearchRequest.of(s -> s
                 *     .index("...")
                 *     .query(q -> q.matchAll())
                 *     .knn(k -> k.field("...").k(5).queryVector(...))
                 *     .size(20)
                 * )
                 * ```
                 * 此时 Elasticsearch 会 融合 query 和 knn 的结果（通过 rank 或 reciprocal_rank_fusion 等策略）。
                 * 最终返回 最多 size 条结果，但 KNN 部分最多贡献 k 条。
                 * 这时 size 和 k 都有作用，但含义不同。
                 *
                 * 所以在你当前的代码（只有 knn，没有 query）中：knn.k 是决定性参数，size 基本不起作用。
                 */
                //.size(NumberOfCalledChatDocuments*4) // 获取更多结果以便合并
        ));

        SearchRequest textSearchRequest  = SearchRequest.of(s -> s
                .index(ElasticConfig.USER_AI_DIALOGUE_MEMORY_INDEX)
                .query(q -> q
                        .bool(b -> b
                                .must(mb -> mb
                                        .multiMatch(m -> m
                                                .query(keyword)
                                                .fields(List.of("original_question", "original_answer"))
                                        )
                                )
                                // 布尔过滤器先把不属于当前用户的对话记录过滤掉,然后再进行关键词搜索
                                .filter(fb -> fb
                                        .term(t -> t
                                                .field("user_id")
                                                .value(userId)
                                        )
                                )
                        )
                )
                .size(NumberOfCalledChatDocuments)
                .source(SourceConfig.of(sc ->
                                sc.filter(i ->i.includes(List.of("original_question","original_answer"))))));

        SearchResponse<Map> knnSearchResponse = client.search(knnSearchRequest, Map.class);
        SearchResponse<Map> textSearchResponse = client.search(textSearchRequest, Map.class);

        List<String> rankedConversationIdList = elasticUtil.reciprocalRankMerge(knnSearchResponse, textSearchResponse, textWeight, knnWeight);

        Map<String,Map> allHitsMap = new HashMap<>();
        List<Map<String,String>> conversationMapList = new ArrayList<>();

        for (Hit<Map> hit : textSearchResponse.hits().hits()) {
            allHitsMap.putIfAbsent(hit.id(), hit.source());
        }
        for (Hit<Map> hit : knnSearchResponse.hits().hits()) {
            allHitsMap.putIfAbsent(hit.id(), hit.source());
        }

        for (String conversationId : rankedConversationIdList) {
            String userQuestion = allHitsMap.get(conversationId).get("original_question").toString();
            String assistantAnswer = allHitsMap.get(conversationId).get("original_answer").toString();

            conversationMapList.add(Map.of("user",userQuestion,"assistant",assistantAnswer));
        }
        if (conversationMapList.size() > NumberOfRemainedChatDocuments) {
            conversationMapList = conversationMapList.subList(0, NumberOfCalledChatDocuments);
        }
        return conversationMapList;
    }
}
