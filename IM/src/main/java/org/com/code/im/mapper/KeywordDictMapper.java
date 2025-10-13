package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KeywordDictMapper {
    List<String> selectAllKeyword();
    void insertManyKeyword(List<String> keywordList);
}
