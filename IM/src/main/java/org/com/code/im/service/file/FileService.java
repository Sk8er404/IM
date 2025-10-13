package org.com.code.im.service.file;

import org.com.code.im.pojo.Files;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface FileService {
    Map insertFile(Map files);
    int deleteFileById(long id,long userId);
    List<Files> searchFileByKeyWords(String keyword,long userId);
    List<Files> searchFileByTime(LocalDateTime startTime, LocalDateTime endTime,long userId);
    List<Files> queryAllFiles(long userId);
    List<Files> queryFileByType(String fileType, long userId);
    List<Files> queryFileByTypes(String[] fileTypes, long userId);
}
