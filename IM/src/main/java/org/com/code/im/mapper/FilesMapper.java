package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.com.code.im.pojo.Files;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FilesMapper {
    void insertFile(Map map);
    int deleteFileById(long userId,long id);
    String getUrl(long id);
    List<Files> searchFileByKeyWords(String keyword,long userId);
    List<Files> searchFileByTime(LocalDateTime startTime, LocalDateTime endTime, long userId);
    List<Files> queryAllFiles(long userId);
    List<Files> queryFileByType(String fileType,long userId);
    List<Files> queryFileByTypes(@Param("fileTypes") String[] fileTypes, @Param("userId") long userId);
}
