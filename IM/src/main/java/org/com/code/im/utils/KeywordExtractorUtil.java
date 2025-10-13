package org.com.code.im.utils;

import com.hankcs.hanlp.HanLP;
import jakarta.annotation.PostConstruct;
import org.com.code.im.ElasticSearch.Service.KeywordDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于HanLP的关键词提取工具
 * 支持TextRank和TF-IDF算法
 */
@Component
public class KeywordExtractorUtil{

    private static RedisTemplate<String, String> strRedisTemplate;
    private static KeywordDictService keywordDictService;
    @Autowired
    @Qualifier("strRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    KeywordDictService dictService;

    @PostConstruct
    public void init(){
        strRedisTemplate=redisTemplate;
        keywordDictService= dictService;
    }


    public static void extractWords(String... content) {

        // 合并多个文本内容为一个字符串
        StringJoiner stringJoiner = new StringJoiner(" ");
        for (String text : content) {
            stringJoiner.add(text);
        }
        String combinedText = stringJoiner.toString();
        // 检查输入是否为空
        if (combinedText.trim().isEmpty())
            return;
        
        // 3. 提取关键词
        /**
         * HanLP 的 extractKeyword 已经很完善：
         * 内置停用词过滤
         * 使用 TextRank 算法计算关键词重要性
         * 自动处理中文分词
         * 按重要性排序返回结果
         */
        List<String> keywords = HanLP.extractKeyword(combinedText,Math.max(combinedText.length()/10,10));

        // 4. 将关键词保存到Redis中
        /**
         * 使用 Redis 的  strRedisTemplate.opsForSet().add()，插入数据会自动去重
         */

        keywordDictService.updateKeywordDict(keywords);
        String[] keywordsArray = keywords.toArray(new String[0]);

        if(keywordsArray.length==0)
            return;
        strRedisTemplate.opsForSet().add("keywords", keywordsArray);
        // 记录最后更新时间
        strRedisTemplate.opsForValue().set("lastUpdateTime",String.valueOf(System.currentTimeMillis()));
    }
}
