package org.com.code.im.ElasticSearch.Service.Impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch._types.SortOrder;
import jakarta.annotation.PreDestroy;
import org.com.code.im.ElasticSearch.Service.ESVideoService;
import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.LangChain4j.Service.EmbeddingService;
import org.com.code.im.exception.ElasticSearchException;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.service.customRecommend.CustomRecommendService;
import org.com.code.im.utils.SentenceSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ESVideoImpl implements ESVideoService {
    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;
    @Autowired
    private ElasticUtil elasticUtil;
    @Autowired
    private EmbeddingService embeddingService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @Override
    public void createVideoIndexDelayed(Map videoData) {
        scheduler.schedule(() -> {
            try {
                /**
                 * 视频的标题一定不为空,但是描述可能为空
                 * 如果视频的描述为空,则使用标题作为描述来切割成文本块
                 */
                if(videoData.get("description")==null)
                    videoData.put("description", "无描述");

                List<String> contentChunks =
                        SentenceSplitter.RagChunker(videoData.get("description").toString());

                List<Map<String, Object>> documents = new ArrayList<>();
                for(int i = 0; i < contentChunks.size(); i++){

                    Map<String, Object> document = new HashMap<>();
                    document.put("chunk_id", videoData.get("id") + "_" + i);
                    document.put("contentParent_id", videoData.get("id"));
                    document.put("title", videoData.get("title"));

                    /**
                     * 如果视频的描述为空,则文本块是由标题进行切割的
                     * contentChunks.get(i)就是标题文本块,
                     * 如果描述不为空,标题和描述共同的切割文本块
                     */
                    List<String> tags = (List<String>) videoData.get("tags");
                    document.put("tags", tags != null ? String.join(" ", tags) : "");
                    document.put("description", contentChunks.get(i));
                    document.put("category",videoData.get("category")!=null?videoData.get("category"):"");

                    document.put("createdAt", videoData.get("createdAt"));
                    documents.add(document);
                }

                //把标题和描述一起向量化作为某个视频文本块的向量描述
                List<String> contentList=documents.stream()
                        .map(doc -> doc.get("title").toString()+"\n"+
                                doc.get("description").toString())
                        .collect(Collectors.toList());

                List<float[]> vectorList=embeddingService.getEmbeddings(contentList);

                elasticUtil.bulkIndex(documents, ElasticConfig.VIDEO_INDEX,vectorList);
                elasticUtil.createIndex(ElasticConfig.VIDEO_AVERAGE_VECTOR_INDEX,videoData.get("id").toString(), vectorList);
            } catch (Exception e) {
                e.printStackTrace();
                throw new ElasticSearchException("创建ES视频索引失败");
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
    public void deleteVideoIndex(Long videoId) {
        try {
            // 构建一个 DeleteByQuery 请求
            DeleteByQueryRequest deleteChunkIndex = DeleteByQueryRequest.of(d -> d
                    .index(ElasticConfig.VIDEO_INDEX)
                    .query(q -> q
                            // 使用 term query 来精确匹配 parentId
                            .term(t -> t
                                    .field("contentParent_id")
                                    .value(videoId)
                            )
                    )
                    // 设置 "wait_for_completion" 为 false，让其在后台执行，请求可以更快返回
                    // ES会启动一个任务来执行删除，对于大量文档的删除非常有用
                    .waitForCompletion(false)
                    // 发生冲突时继续执行
                    .conflicts(Conflicts.Proceed)
            );

            DeleteRequest deleteAverageVectorIndex = DeleteRequest.of(d -> d
                    .index(ElasticConfig.VIDEO_AVERAGE_VECTOR_INDEX)
                    .id(videoId.toString())
            );

            // 执行请求
            client.deleteByQuery(deleteChunkIndex);
            client.delete(deleteAverageVectorIndex);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ElasticSearchException("删除ES视频索引失败");
        }
    }

    @Override
    public List<Long> searchVideoByHybridSearch(String keyword, int page, int size) {
        try {
           return elasticUtil.hybridSearch(keyword, ContentType.VIDEO, Arrays.asList("title", "description", "tags"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("搜索视频失败");
        }
    }

    @Override
    public List<Long> searchVideosByTime(String startTime, String endTime, int page, int size) {
        try {
            SearchRequest searchRequest =
                    elasticUtil.getSearchByTimeRequest(startTime, endTime, page, size,ElasticConfig.VIDEO_INDEX,SortOrder.Asc);
            return elasticUtil.getIds(searchRequest);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("搜索视频失败");
        }
    }
}
