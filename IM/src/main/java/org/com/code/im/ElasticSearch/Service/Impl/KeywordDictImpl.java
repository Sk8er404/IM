package org.com.code.im.ElasticSearch.Service.Impl;

import jakarta.annotation.PostConstruct;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.KeywordDictMapper;
import org.com.code.im.ElasticSearch.Service.KeywordDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class KeywordDictImpl implements KeywordDictService {

    private static Set<String> keywordDict=new HashSet<>();

    @Autowired
    private KeywordDictMapper keywordDictMapper;
    @Autowired
    @Qualifier("strRedisTemplate")
    private RedisTemplate<String, String> strRedisTemplate;

    @PostConstruct
    public void init(){
        strRedisTemplate.opsForValue().set("lastUpdateTime",String.valueOf(System.currentTimeMillis()));
        Set<String> keywordSet = strRedisTemplate.opsForSet().members("keywords");
        if (keywordSet != null&&!keywordSet.isEmpty())
            keywordDict.addAll(keywordSet);
        List<String> keywordList = keywordDictMapper.selectAllKeyword();
        keywordDict.addAll(keywordList);
    }


    @Override
    public Set<String> getLatestKeyword() {
        return keywordDict;
    }

    @Override
    public long getLastUpdateTime(){
        String lastUpdateTime = strRedisTemplate.opsForValue().get("lastUpdateTime");
        return Long.parseLong(lastUpdateTime);
    }

    @Override
    public void updateKeywordDict(List<String> newKeywordDict) {
        keywordDict.addAll(newKeywordDict);
    }

    @Scheduled(fixedRate = 180000)
    @Transactional
    public void saveKeyword() {
        try {
            Set<String> keywordList = strRedisTemplate.opsForSet().members("keywords");
            if (keywordList == null||  keywordList.isEmpty())
                return;
            List<String> keywords = keywordList.stream().toList();
            keywordDictMapper.insertManyKeyword(keywords);
            strRedisTemplate.delete("keywords");
        }catch (Exception e){
            e.printStackTrace();
            throw new DatabaseException("关键词插入失败");
        }
    }
}
