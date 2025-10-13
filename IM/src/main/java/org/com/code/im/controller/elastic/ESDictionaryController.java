package org.com.code.im.controller.elastic;

import jakarta.servlet.http.HttpServletResponse;
import org.com.code.im.ElasticSearch.Service.KeywordDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * ES远程词典控制器
 * 专门为Elasticsearch IK分词器提供远程词典接口
 */
@RestController
public class ESDictionaryController {
    
    @Autowired
    private KeywordDictService keywordDictService;

    long lastFetchDictTime = 0;
    String dictContent = "";
    
    /**
     * 为ES IK分词器提供扩展词典
     * 这是IK分词器会定期访问的接口
     */
    @GetMapping("/api/es/dict/update")
    public ResponseEntity<String> getLatestDictionary(HttpServletResponse response) {
        // 1. 获取所有已过滤的关键词
        /**
         * 因为ES搜索引擎的IK分词器一般是每隔60秒钟访问一次远程词典,如果远程词典有更新，
         * 则下载远程词典直接在内存中和本地词典合并(直接在内存中的操作！！不是先下载到文件里面再合并!!)
         */
        Set<String> keywords=keywordDictService.getLatestKeyword();

        /**
         * IK 分词器判断是否更新远程词典的机制是基于 HTTP 的 Last-Modified 和 ETag 头部：
         * Last-Modified：如果 IK 分词器缓存的 Last-Modified 时间小于服务端返回的新时间，则认为有更新。
         * ETag：如果 ETag 不同（即内容变化），也会触发更新。
         * 注意：IK 分词器内部比较 Last-Modified 时通常只精确到秒级。
         */
        // 2. 设置HTTP头信息（IK分词器依赖这些头信息判断是否需要更新）
        response.setHeader("Last-Modified",
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .format(new Date(keywordDictService.getLastUpdateTime())));

        response.setHeader("Content-Type", "text/plain; charset=UTF-8");

        // 3. 格式化词典内容（每行一个词）
        if(lastFetchDictTime < keywordDictService.getLastUpdateTime()){
            dictContent = String.join("\n", keywords) + "\n";
            lastFetchDictTime = System.currentTimeMillis();
        }

        // 4. 返回词典内容
        return ResponseEntity.ok(dictContent);
    }
}