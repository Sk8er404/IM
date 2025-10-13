package org.com.code.im.ElasticSearch.Service.Impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.com.code.im.ElasticSearch.Service.ESUserService;
import org.com.code.im.ElasticSearch.Service.ElasticUtil;
import org.com.code.im.ElasticSearch.config.ElasticConfig;
import org.com.code.im.exception.ElasticSearchException;
import org.com.code.im.service.customRecommend.CustomRecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class ESUserImpl implements ESUserService {

    @Autowired
    @Qualifier("node1")
    private ElasticsearchClient client;
    @Autowired
    private ElasticUtil elasticUtil;

    @Override
    public void createUserIndex(Map userMap) {
        try {
            StringBuilder embeddingText = new StringBuilder();
            embeddingText.append(userMap.get("bio"));

            elasticUtil.createIndex(userMap, ElasticConfig.USER_INDEX,embeddingText.toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("创建ES用户索引失败"+e.getMessage());
        }
    }

    @Override
    public void deleteUserIndex(Long userId) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(ElasticConfig.USER_INDEX)
                    .id(userId.toString()));
            client.delete(request);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ElasticSearchException("删除ES视频索引失败");
        }
    }

    @Override
    public void updateUserIndex(Map userMap) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("userName", userMap.get("userName"));
            updates.put("bio", userMap.get("bio"));

            client.update(u -> u
                            .index(ElasticConfig.USER_INDEX)
                            .id(userMap.get("id").toString())
                            .doc(updates),
                    Map.class
            );
        } catch (IOException e) {
            e.printStackTrace();
            throw new ElasticSearchException("更新ES用户索引失败");
        }
    }

    @Override
    public List<Long> searchUserByName(String userName, int page, int size) {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ElasticConfig.USER_INDEX)
                    .query(q -> q
                            .bool(b -> b
                                    .should(sh -> sh
                                            .term(t -> t
                                                    .field("userName")
                                                    .value(userName)))
                                    .should(sh -> sh
                                            .prefix(p -> p
                                                    .field("userName")
                                                    .value(userName)))))
                    .sort(so -> so
                            .field(f -> f
                                    .field("createdAt")
                                    .order(SortOrder.Desc)))
                    .from(page * size)
                    .size(size));
            
            SearchResponse<Object> searchResponse = client.search(searchRequest, Object.class);
            List<Long> ids = new ArrayList<>();
            for (Hit<Object> hit : searchResponse.hits().hits()) {
                ids.add(Long.parseLong(hit.id()));
            }
            return ids;
        }catch (Exception e) {
            e.printStackTrace();
            throw new ElasticSearchException("搜索用户失败");
        }
    }
}
