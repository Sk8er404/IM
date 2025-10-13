package org.com.code.im.ElasticSearch.Service.Impl;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import org.com.code.im.pojo.enums.ContentType;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import jakarta.annotation.PreDestroy;
import org.com.code.im.ElasticSearch.Service.ESPostService;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.Service.EmbeddingService;
import org.com.code.im.exception.ElasticSearchException;
import org.com.code.im.utils.SentenceSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ESPostImpl implements ESPostService {
    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;
    @Autowired
    private ElasticUtil elasticUtil;
    @Autowired
    private EmbeddingService embeddingService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Override
    public void createPostIndexDelayed(Map postData) {
        scheduler.schedule(() -> {
            try {
                List<String> contentChunks = SentenceSplitter.RagChunker(postData.get("content").toString());
                List<Map<String, Object>> documents = new ArrayList<>();
                for(int i = 0; i < contentChunks.size(); i++){
                    Map<String, Object> document = new HashMap<>();
                    document.put("chunk_id", postData.get("id") + "_" + i);
                    document.put("contentParent_id", postData.get("id"));
                    document.put("title", postData.get("title"));
                    List<String> tags = (List<String>) postData.get("tags");
                    document.put("tags", tags != null ? String.join(" ", tags) : "");
                    document.put("content", contentChunks.get(i));
                    document.put("createdAt", postData.get("createdAt"));
                    documents.add(document);
                }

                // 把标题和内容一起向量化作为某个帖子文本块的向量描述
                List<String> contentList=documents.stream()
                        .map(doc -> doc.get("title").toString()+"\n"+
                                doc.get("content").toString())
                        .collect(Collectors.toList());

                List<float[]> vectorList=embeddingService.getEmbeddings(contentList);

                //创建帖子文本块的索引
                elasticUtil.bulkIndex(documents, ElasticConfig.POST_INDEX,vectorList);
                //创建帖子平均向量的索引
                elasticUtil.createIndex(ElasticConfig.POST_AVERAGE_VECTOR_INDEX,postData.get("id").toString(),vectorList);
            } catch (Exception e) {
                e.printStackTrace();
                throw new ElasticSearchException("创建ES帖子索引失败");
            }
        }, ElasticConfig.INDEX_CREATE_DELAY_SECONDS, TimeUnit.SECONDS);
    }



    @PreDestroy
    public void shutdownScheduler() throws IOException {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void deletePostIndex(Long postId) {
        try {
            // 构建一个 DeleteByQuery 请求
            DeleteByQueryRequest deleteChunkIndex = DeleteByQueryRequest.of(d -> d
                    .index(ElasticConfig.POST_INDEX)
                    .query(q -> q
                            // 使用 term query 来精确匹配 parentId
                            .term(t -> t
                                    .field("contentParent_id")
                                    .value(postId)
                            )
                    )
                    // 设置 "wait_for_completion" 为 false，让其在后台执行，请求可以更快返回
                    // ES会启动一个任务来执行删除，对于大量文档的删除非常有用
                    .waitForCompletion(false)
                    // 发生冲突时继续执行
                    .conflicts(Conflicts.Proceed)
            );
            DeleteRequest deleteAverageVectorIndex = DeleteRequest.of(d -> d
                    .index(ElasticConfig.POST_AVERAGE_VECTOR_INDEX)
                    .id(postId.toString())
            );

            // 执行请求
            client.deleteByQuery(deleteChunkIndex);
            client.delete(deleteAverageVectorIndex);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ElasticSearchException("删除ES帖子索引失败");
        }
    }

    @Override
    public void updatePostIndex(Map PostData){
        try {
            Map<String, Object> updates = new HashMap<>();

            updates.put("id", PostData.get("id"));
            updates.put("title", PostData.get("title"));
            updates.put("content", PostData.get("content"));
            updates.put("tags", PostData.get("tags"));

            deletePostIndex((Long)PostData.get("id"));
            createPostIndexDelayed(PostData);

        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("更新ES帖子索引失败");
        }
    }
    
    @Override
    public List<Long> searchPostByHybridSearch(String keyword, int page, int size) {
        try {
            return elasticUtil.hybridSearch(keyword,ContentType.POST, List.of("title", "content","tags"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("搜索帖子失败");
        }
    }

    @Override
    public List<Long> searchPostsByTime(String startTime, String endTime, int page, int size) {
        try {
            SearchRequest searchRequest = elasticUtil.getSearchByTimeRequest(startTime, endTime, page, size, ElasticConfig.POST_INDEX, SortOrder.Desc);
            SearchResponse<Object> searchResponse = client.search(searchRequest, Object.class);
            
            List<Long> ids = new ArrayList<>();
            for (Hit<Object> hit : searchResponse.hits().hits()) {
                ids.add(Long.parseLong(hit.id()));
            }
            return ids;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("搜索帖子失败");
        }
    }
}