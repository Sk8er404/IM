package org.com.code.im.ElasticSearch.Service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;

import org.com.code.im.LangChain4j.Service.EmbeddingService;
import org.com.code.im.LangChain4j.config.Model;
import org.com.code.im.pojo.dto.VectorDoc;
import org.com.code.im.pojo.enums.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ElasticUtil {
    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;
    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 搜索相似度阈值,只有高于这个阈值，才会被knn的向量相似度查询命中返回给用户,
     * AI的RAG搜索的阈值我设置成0.8，推荐搜索的阈值我设置成0.7
     */
    public static final float RAGSearchMinSimilarity = 0.8f;
    public static final float UserRecommendSearchMiniSimilarity = 0.8f;
    public static final int resultLimit  = 30;

    public static final double textWeight = 0.7;
    public static final double knnWeight = 1.0;

    public static final int RRF_CONSTANT=60;

    /**
     *  单个文档索引方法，只用于用户注册时候创建索引
     * 'createdAt' 字段被格式化为字符串以确保与 Elasticsearch 的日期格式兼容。
     *
     * @param data 文档数据，以Map形式表示。
     * @param indexName 索引名称。
     * @param EmbeddingText 需要转化成语义向量的嵌入文本
     * @param “vector” 语义向量
     * @throws IOException 如果在索引操作期间发生I/O错误。
     */
    public void createIndex(Map<String, Object> data, String indexName,String EmbeddingText) throws IOException {
        LocalDateTime createdAt = (LocalDateTime) data.get("createdAt");
        // 格式化并截断到秒，存储为字符串以避免JSON序列化问题。
        String truncatedTime = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        data.put("createdAt", truncatedTime);

        /**
         * 当使用 Elasticsearch Java 客户端的 .document(data) 方法时，客户端内部会使用一个库（通常是 Jackson）将您的 Map 对象转换成 JSON 字符串。
         * 对于 Jackson 来说，无论是 float[] 还是 Float[]，它默认都会被序列化成 Elasticsearch dense_vector 字段所期望的、标准的 JSON 数组格式：
         * [0.123, 0.456, 0.789, ...]
         * 因此，从 Elasticsearch 服务器接收到的最终数据来看，两者没有区别
         * 但是，强烈不推荐这样做
         * 尽管它能工作，但在实际应用中，特别是处理机器学习和向量数据时，始终应该使用 float[] 而不是 Float[]。
         * 主要原因有以下两点：
         * 内存效率:
         * float[]: 这是一个原始类型数组。它是一块连续的内存空间，直接存储所有的浮点数值。对于一个 1024 维的向量，它占用的内存大约是 1024 * 4 字节。
         * Float[]: 这是一个对象数组。它存储的不是浮点数值本身，而是指向 1024 个独立的 Float 对象的内存地址（引用）。每个 Float 对象除了包含一个 float 值外，还有额外的对象头开销。
         * 结论：Float[] 会比 float[] 占用多得多的内存。当您处理成千上万个文档时，这种内存差异会变得非常巨大，可能导致不必要的内存压力甚至 OutOfMemoryError。
         * 性能:
         * 由于 float[] 的内存是连续的，CPU 访问它时可以更好地利用缓存（缓存局部性），计算速度更快。
         * 处理 Float[] 时，程序需要先通过引用找到每个 Float 对象，然后再从对象中取出（这个过程称为“拆箱”）原始的 float 值，这会带来微小但可以累积的性能开销。
         */
        float[] vector=embeddingService.getEmbedding(EmbeddingText);
        data.put("vector",vector);

        client.index(i -> i
                .index(indexName)
                .id(data.get("id").toString())
                .document(data)
        );
    }

    /**
     * 批量索引方法，用于帖子视频的文本块列表批量创建对应索引
     * @param documents
     * @param indexName
     * @param vectorList
     * @throws IOException
     */
    public void bulkIndex(List<Map<String, Object>> documents, String indexName,List<float[]> vectorList) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return; // 如果没有文档，则直接返回
        }
        // 格式化并截断到秒，存储为字符串以避免JSON序列化问题。
        LocalDateTime createdAt = (LocalDateTime) documents.get(0).get("createdAt");
        String truncatedTime = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        for (int i = 0; i < documents.size(); i++) {
            documents.get(i).put("vector", vectorList.get(i));
            // 对每个文档进行日期格式化处理
            documents.get(i).put("createdAt", truncatedTime);
        }

        // 1. 创建一个BulkOperation列表
        List<BulkOperation> bulkOperations = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            /**
             * 如何区分文档的元数据（Metadata）和文档的内容本身（Source）。
             *
             * 1. 核心概念：_id vs 自定义的 chunk_id
             * 在Elasticsearch中，每个存入的文档都有一些内置的元数据字段（meta-fields），它们用于管理文档。最重要的一个就是 _id。
             *
             * _id (元数据ID)：
             *
             * 这是ES用来唯一标识一个文档的字符串。它和 _index 一起构成了文档的唯一地址。
             * ES所有的内部操作，如获取、更新、删除特定文档，都依赖这个 _id。
             * 它不属于我们在 mappings 中定义的任何字段，而是文档的“身份证号”。
             *
             * chunk_id (自定义的字段)：
             *
             * 这是我们在 mappings 中明确定义的一个字段，它的类型是 keyword。
             * 它属于文档的内容本身（在ES中称为 _source 对象）。
             * 我们可以像查询 title 或 tags 一样，对 chunk_id 字段进行搜索、聚合等操作。
             *
             * 结论：_id 是ES层面的身份证，而 chunk_id 是您业务数据的一部分。它们是两个完全不同的东西。
             *
             * 2. 解释的代码 .id(...) 和 .document(...)
             *
             * .index(idx -> idx
             *     .index(indexName)
             *     // 这行代码是告诉ES：“请将这个文档的元数据 `_id` 设置为这个字符串”
             *     .id(doc.get("id")+"_"+sequenceNumber)
             *     // 这行代码是告诉ES：“请将 `doc` 这个Map里的所有内容作为文档的 `_source` 存储起来”
             *     .document(doc)
             * )
             * .id(...) 的作用：这个方法是专门用来设置文档元数据 _id 的。它后面的值就是您希望ES为这个文档分配的“身份证号”。
             *
             * .document(doc) 的作用：这个方法会将您传入的整个 doc Map对象序列化成一个JSON对象，并将其作为文档的 _source 存储。ES在收到这个 _source 后，会根据您为 indexName 定义的 mappings 来解析里面的字段（如 chunk_id, contentParent_id, title 等），并对它们进行相应的处理（比如用ik_max_word分词器处理title）。
             *
             * 所以，您的代码并没有去“指定索引某个field值为map中的某个键对应的值”，而是分了两步：
             *
             * 用 .id() 方法设置了文档的外部ID（元数据 _id）。
             *
             * 用 .document() 方法设置了文档的内部所有内容（_source），ES会自动根据 mappings 来匹配 _source 里的字段。
             */
            // 为每个文档创建一个 "index" 操作
            BulkOperation operation = BulkOperation.of(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id((String)doc.get("chunk_id")) // 这里需要一个唯一的ID
                            .document(doc)
                    )
            );
            bulkOperations.add(operation);
        }

        // 2. 构建并执行 BulkRequest
        BulkRequest bulkRequest = BulkRequest.of(b -> b
                .operations(bulkOperations)
        );

        client.bulk(bulkRequest);
    }

    //创建存放帖子或者视频内容的平均向量的索引
    public void createIndex(String indexName,String id,List<float[]> chunkVectorList) throws IOException {
        float[] averageVector = new float[Model.DimensionOfEmbeddingModel];
        for(float[] array:chunkVectorList){
            for(int i=0;i<array.length;i++){
                averageVector[i]+=array[i];
            }
        }
        for (int i=0;i<averageVector.length;i++){
            float value = averageVector[i]/chunkVectorList.size();
            averageVector[i] = value;
        }
        Map<String, Object> average_index = new HashMap<>();
        average_index.put("average_vector", averageVector);
        average_index.put("id", id);

        client.index(i -> i
                .index(indexName)
                .id(id)
                .document(average_index)
        );
    }
    public List<Long> searchContentBySimilarAverageVector(float[] array, String indexName, int similarContentNumber,List<String> excludeContentIdList) throws IOException {
        // 将 float[] 转换为 List<Float>
        List<Float> queryVector = new ArrayList<>();
        for (float f : array) {
            queryVector.add(f);
        }


        /**
         * 如果将 knn() 向量查询和 query() 布尔查询（用于排除ID）作为两个独立的、平级的参数设置在了顶层的 SearchRequest.Builder 上。
         *
         * 错误的代码结构示意：
         *
         * Java
         *
         * // SearchRequest.Builder
         * builder
         *     .knn(...)      // 这是一个顶层查询
         *     .query(...)    // 这是另一个平级的顶层查询
         * 这导致无意中构建了一个混合搜索 (Hybrid Search) 请求，而不是一个带前置过滤的 k-NN 搜索 (Filtered k-NN Search) 请求。
         *
         * 三、错误模式的真实含义 (What the Incorrect Mode Actually Does)
         * 您构建的那个“不符合要求”的查询模式，在 Elasticsearch 中被称为 混合搜索 (Hybrid Search)。它的工作流程如下：
         *
         * 并行执行两个查询：
         *
         * k-NN 查询部分：ES 在全部文档上运行向量相似度搜索，找出与查询向量最相似的 k 个结果，并为它们计算一个向量相关性得分。
         *
         * Bool 查询部分：ES 同时在全部文档上运行布尔查询（在您的情况下是 must_not 排除ID），找出符合条件的文档，并为它们计算一个传统的文本相关性得分（如 BM25，尽管在您的例子中得分可能为0）。
         *
         * 合并和重排结果 (Rescoring)：
         *
         * ES 获取来自上述两个查询的两组结果。
         *
         * 它使用一种融合算法（如 RRF - Reciprocal Rank Fusion）来合并这两组结果，综合考虑两种得分，生成一个最终的、重新排序的列表返回给用户。
         *
         * 结论就是： 在这种模式下，must_not 过滤器仅仅影响了布尔查询那一路的结果，但完全没有对 k-NN 查询本身起到任何过滤作用。
         * k-NN 搜索依然我行我素地在全量数据中寻找最近邻，所以它当然有可能找回那些您想排除的 ID。
         *
         * 四、解决方案 (Solution)
         * 正确的做法是，明确告诉 Elasticsearch：“请先应用我的过滤条件，然后在满足条件的文档子集上，再执行 k-NN 搜索。”
         *
         * 这需要将布尔过滤器作为 knn 查询的一个内部参数，而不是一个平级的查询。
         *
         * 正确的实现步骤：
         *
         * 将过滤逻辑构建成一个独立的 Query 对象。
         *
         * Java
         *
         * Query filterQuery = Query.of(q -> q
         *     .bool(b -> b
         *         .mustNot(m -> m
         *             .ids(i -> i.values(excludeContentIdListStr))
         *         )
         *     )
         * );
         * 在 knn() 方法的 lambda 表达式内部，调用 filter() 方法，并将上面创建的 filterQuery 作为参数传入。
         * filter() 方法是属于 KnnSearch.Builder 的，而不是 SearchRequest.Builder 的。
         *
         * 正确的代码结构示意：
         *
         * Java
         *
         * // builder 是 SearchRequest.Builder
         * builder.knn(k -> k  // k 是 KnnSearch.Builder
         *         .field(...)
         *         .queryVector(...)
         *         .k(...)
         *         // 关键在这里：将 filter 作为 knn 查询的一个参数
         *         .filter(filterQuery)
         * );
         * 通过这种方式，Elasticsearch 就会按照预期的“先过滤，后搜索”的逻辑执行，从而返回精确、干净且符合去重要求的推荐结果。
         */
        SearchRequest.Builder builder = new SearchRequest.Builder();

        // 我们要构建的布尔过滤查询
        Query filterQuery = Query.of(q -> q
                .bool(b -> b
                        .mustNot(m -> m
                                .ids(i -> i
                                        .values(excludeContentIdList)
                                )
                        )
                )
        );

        builder.index(indexName)
                .knn(k -> k
                        .field("average_vector")
                        .queryVector(queryVector)
                        .k(similarContentNumber)
                        .numCandidates(Math.max(similarContentNumber * 2, 100))// 通常 numCandidates 应该是k的倍数
                        .similarity(UserRecommendSearchMiniSimilarity)
                        .filter(filterQuery))
                        //只返回 contentParent_id字段，减少网络传输
                        .source(s->s.filter(src->src.includes("id")));

        // 使用获取到的向量进行相似性搜索
        SearchRequest searchRequest = builder.build();

        SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

        List<Long> similarContentIds = new ArrayList<>();
        for (Hit<Map> hit : searchResponse.hits().hits()) {
            similarContentIds.add(Long.parseLong(hit.id()));
        }
//        /**
//         * 删除相似内容列表中当前帖子或者视频的id
//         */
//        similarContentIds.removeIf(similarContentId -> similarContentId.equals(id));

        return similarContentIds;
    }



    public List<float[]> multiGetVectorByIds(List<Long> contentIdList,String indexName) throws IOException {
        if (contentIdList == null || contentIdList.isEmpty()) {
            float[] zeroVector = new float[Model.DimensionOfEmbeddingModel];
            List<float[]> newUserVectorList = new ArrayList<>();
            newUserVectorList.add(zeroVector);
            return newUserVectorList;
        }

        // 1. 将 List<Long> 转换为 List<String>
        List<String> idStringList = contentIdList.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        // 2. 构建 MgetRequest
        MgetRequest mgetRequest = MgetRequest.of(m -> m
                .index(indexName)
                .ids(idStringList)
                // 指定只返回向量字段，减少网络传输
                .sourceIncludes("average_vector")
        );
        return executeMultiRequest(mgetRequest,VectorDoc.class,VectorDoc::getAverage_vector);

        /**
         * 存储向量的代码如下:
         *
         * float[] average_vector = new float[]{0.1f, 0.2f, ...};
         * data.put("average_vector", average_vector);
         *
         * ES Java客户端 (序列化): 当调用.document(data)时，客户端内部的JSON库（通常是Jackson）会将的data Map转换为JSON字符串。在这个过程中，float[] 被转换成了一个JSON数组。
         *
         * JSON
         *
         * {
         *   "average_vector": [0.10000000149011612, 0.20000000298023224, ...]
         * }
         * Elasticsearch (存储与响应): ES接收并存储这个JSON。当您查询时，它会将这个JSON原样返回给的客户端。
         *
         * ES Java客户端 (反序列化): 这是最关键的一步！客户端收到了包含"average_vector": [ ... ]的JSON。
         * 由于我们指定了用 VectorDoc.class来接收结果，所以客户端会把JSON对象反序列化为一个VectorDoc。
         * 
         * 如果你的ElasticSearch的向量索引部分的名字叫做 average_vector
         * 确保VectorDoc.class里面的 float[] 名字也叫 average_vector , ES 返回的包含 average_vector 的字段名字的JSON要反序列化成一个float[],
         * 所以确保用作接收结果的类的 float[] 也叫做 average_vector
         */
    }

    /**
     * 执行多个get查询请求, 并返回结果
     * @param mgetRequest
     * @param clazz
     * @param extractor
     * @return
     * @param <T>
     * @param <R>
     * @throws IOException
     */
    public <T, R> List<R> executeMultiRequest(
            MgetRequest mgetRequest,
            Class<T> clazz,
            Function<T, R> extractor) throws IOException {

        MgetResponse<T> mgetResponse = client.mget(mgetRequest, clazz);

        List<R> resultList = new ArrayList<>();

        for (MultiGetResponseItem<T> doc : mgetResponse.docs()) {
            if (doc.result().found()) {
                T source = doc.result().source();
                R extractedValue = extractor.apply(source);
                if (extractedValue != null) {
                    resultList.add(extractedValue);
                }
            }
        }

        return resultList;
    }

    /**
     * 面向用户的“帖子搜索”功能
     * 目标：用户输入关键词，返回一个按相关性排序的完整帖子列表。
     * @param keyword
     * @param contentType
     * @param textSearchFields
     * @return
     * @throws IOException
     */
    public List<Long> hybridSearch(String keyword, ContentType contentType, List<String> textSearchFields) throws IOException {
        // Step 1: 将用户关键词转换为向量，用于后续的 KNN 搜索
        float[] queryVectorArray = embeddingService.getEmbedding(keyword);
        List<Float> queryVector = new ArrayList<>();
        for (float f : queryVectorArray) {
            queryVector.add(f);
        }

        // 执行基于关键词的全文检索（BM25），返回匹配的文本块
        SearchResponse<Map> textSearchResponse = performTextSearch(keyword, contentType.getType(), textSearchFields);

        // 执行基于向量的 KNN 搜索，返回语义相似的文本块
        SearchResponse<Map> knnSearchResponse = performKnnSearch(queryVector, contentType.getType());

        // 使用 RRF（Reciprocal Rank Fusion）算法融合两种搜索结果，
        // 得到按综合相关性排序的文本块 ID 列表（rankedChunkIds）
        List<String> rankedChunkIds = reciprocalRankMerge(textSearchResponse, knnSearchResponse, textWeight, knnWeight);

        // 构建文本块 ID → 父帖子 ID（contentParent_id）的映射关系
        // 合并两个搜索结果中的所有文本块，避免遗漏
        Map<String, Object> chunkToParentMap = new HashMap<>();
        for (Hit<Map> hit : textSearchResponse.hits().hits()) {
            chunkToParentMap.putIfAbsent(hit.id(), hit.source().get("contentParent_id"));
        }
        for (Hit<Map> hit : knnSearchResponse.hits().hits()) {
            chunkToParentMap.putIfAbsent(hit.id(), hit.source().get("contentParent_id"));
        }
        Map<Long, Double> scoreMap = new HashMap<>();

        int rank=1;
        for(String chunkId : rankedChunkIds){
            Double score = 1.0/(rank+RRF_CONSTANT);
            scoreMap.merge((Long) chunkToParentMap.get(chunkId),score,Double::sum);
            rank++;
        }
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 面向AI的“知识块检索”功能
     * 目标：AI根据用户问题，获取一个按相关性排序的文本块列表，作为生成答案的上下文。
     * @param keyword
     * @param contentType
     * @param textSearchFields
     * @return
     * @throws IOException
     */
    public List<Map<String,Object>> ragHybridSearch(String keyword,ContentType contentType, List<String> textSearchFields) throws IOException {
        float[] queryVectorArray = embeddingService.getEmbedding(keyword);
        List<Float> queryVector = new ArrayList<>();
        for (float f : queryVectorArray) {
            queryVector.add(f);
        }

        SearchResponse<Map> textSearchResponse = performTextSearch(keyword, contentType.getType(), textSearchFields);

        SearchResponse<Map> knnSearchResponse = performKnnSearch(queryVector, contentType.getType());

        List<String> rankedChunkIds = reciprocalRankMerge(textSearchResponse, knnSearchResponse, textWeight, knnWeight);

        Map<String, Map> allHitsMap = new HashMap<>();
        for (Hit<Map> hit : textSearchResponse.hits().hits()) {
            allHitsMap.putIfAbsent(hit.id(), hit.source());
        }
        for (Hit<Map> hit : knnSearchResponse.hits().hits()) {
            allHitsMap.putIfAbsent(hit.id(), hit.source());
        }
        List<Map<String,Object>> ragHybridSearchResultList = new ArrayList<>();
        for (String chunkId : rankedChunkIds) {
            ragHybridSearchResultList.add(allHitsMap.get(chunkId.toString()));
        }
        return ragHybridSearchResultList;
    }

    public SearchResponse<Map> performTextSearch(String keyword,String indexName, List<String> textSearchFields) throws IOException {
        // 2. 执行关键字搜索 (Text Search)
        Query textQuery = Query.of(q -> q
                .multiMatch(m -> m
                        .query(keyword)
                        .fields(textSearchFields)));

        SearchRequest textSearchRequest  = SearchRequest.of(s -> s
                .index(indexName)
                .query(textQuery)
                .size(resultLimit * 2));// 获取更多结果以便合并

        return client.search(textSearchRequest, Map.class);
    }

    public SearchResponse<Map> performKnnSearch(List<Float> queryVector,String indexName) throws IOException {
        // 3. 执行向量搜索 (KNN Search)
        SearchRequest knnSearchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .knn(k -> k
                        .field("vector")
                        .queryVector(queryVector)
                        .k(10)
                        .numCandidates(100)
                        .similarity(RAGSearchMinSimilarity)
                )
                .size(resultLimit * 2) // 获取更多结果以便合并
        );
        return client.search(knnSearchRequest, Map.class);
    }


    public List<String> reciprocalRankMerge(
            SearchResponse<Map> textResponse,
            SearchResponse<Map> knnResponse,
            double textWeight,
            double knnWeight) {

        Map<String, Double> rrfScores = new HashMap<>();

        // 处理关键字搜索结果：按顺序分配 rank（从 1 开始）
        List<Hit<Map>> textHits = textResponse.hits().hits();
        for (int i = 0; i < textHits.size(); i++) {
            String id = textHits.get(i).id();
            int rank = i + 1; // rank 从 1 开始
            double rrf = textWeight * (1.0 / (RRF_CONSTANT + rank));
            rrfScores.merge(id, rrf, Double::sum); // 累加（支持重复ID）
        }

        // 处理向量搜索结果
        List<Hit<Map>> knnHits = knnResponse.hits().hits();
        for (int i = 0; i < knnHits.size(); i++) {
            String id = knnHits.get(i).id();
            int rank = i + 1;
            double rrf = knnWeight * (1.0 / (RRF_CONSTANT + rank));
            rrfScores.merge(id, rrf, Double::sum);
        }

        // 按 RRF 分数降序排序，返回 ID 列表
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 构建搜索请求，用于查找特定日期范围内的文档，支持排序和分页。
     *
     * @param startTime 开始日期，格式为 "yyyy-MM-dd"。
     * @param endTime 结束日期，格式为 "yyyy-MM-dd"。
     * @param page 分页的页码。
     * @param size 每页结果数量。
     * @param indexName 要搜索的索引名称。
     * @param sortOrder 'createdAt' 字段的排序顺序（升序或降序）。
     * @return 配置好的SearchRequest对象。
     */
    public SearchRequest getSearchByTimeRequest(String startTime, String endTime, int page, int size, String indexName, SortOrder sortOrder) {
        LocalDate startDate = LocalDate.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalDate endDate = LocalDate.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 转换为LocalDateTime并格式化为字符串，以匹配ES中的存储格式。
        String startDateTime = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String endDateTime = endDate.atTime(23, 59, 59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        return new SearchRequest.Builder()
                .index(indexName)
                .query(q -> q
                        .range(r -> r
                                .date(t->t
                                        .gte(startDateTime)
                                        .lte(startDateTime)
                                )
                        )
                )
                .sort(s -> s
                        .field(f -> f
                                .field("createdAt")
                                .order(sortOrder)
                        )
                )
                .from(page * size)
                .size(size)
                .build();
    }

    /**
     * 执行搜索请求并从响应中提取文档ID。
     *
     * @param searchRequest 要执行的搜索请求。
     * @return 文档ID列表，以Long类型表示。
     * @throws IOException 如果在搜索操作期间发生I/O错误。
     */
    public List<Long> getIds(SearchRequest searchRequest) throws IOException {
        // 我们使用Object.class因为我们不需要反序列化源数据，只需要元数据（_id）。
        SearchResponse<Object> searchResponse = client.search(searchRequest, Object.class);

        List<Hit<Object>> hits = searchResponse.hits().hits();
        List<Long> ids = new ArrayList<>();
        for (Hit<Object> hit : hits) {
            ids.add(Long.parseLong(hit.id()));
        }
        return ids;
    }
}