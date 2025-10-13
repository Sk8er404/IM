package org.com.code.im.ElasticSearch.Service;

import java.util.List;
import java.util.Set;

/**
 * 内容分析服务
 * 整合关键词提取、词典管理等功能
 */
public interface KeywordDictService {

    Set<String> getLatestKeyword();
    long getLastUpdateTime();
    void updateKeywordDict(List<String> keywordList);
} 