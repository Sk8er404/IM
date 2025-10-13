package org.com.code.im.service.file.impl;

import org.com.code.im.exception.DatabaseException;
import org.com.code.im.mapper.FilesMapper;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.pojo.Files;
import org.com.code.im.service.file.FileService;
import org.com.code.im.utils.OSSUtil;
import org.com.code.im.utils.SnowflakeIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FileImpl implements FileService {
    @Autowired
    private FilesMapper filesMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    private OSSUtil ossUtil;

    @Override
    public Map insertFile(Map files) {
        try {
            files.put("id", SnowflakeIdUtil.fileIdWorker.nextId());
            files.put("userName",userMapper.selectUserNameById((long)files.get("userId")));
            filesMapper.insertFile(files);
            return files;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("插入文件信息失败");
        }
    }

    @Override
    public int deleteFileById(long id, long userId) {
        try {
            int deleteCount = 0;
            String filePath = filesMapper.getUrl(id);
            deleteCount = filesMapper.deleteFileById(userId, id);
            if (deleteCount >0) {
                ossUtil.deleteFile(filePath);
            }
            return deleteCount;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DatabaseException("删除文件失败");
        }
    }

    @Override
    public List<Files> searchFileByKeyWords(String keyword,long userId) {
        try {
            return filesMapper.searchFileByKeyWords(keyword,userId);
        } catch (Exception e) {
            throw new DatabaseException("按关键字查询文件失败");
        }
    }

    @Override
    public List<Files> searchFileByTime(LocalDateTime startTime, LocalDateTime endTime,long userId) {
        try {
            return filesMapper.searchFileByTime(startTime, endTime, userId);
        } catch (Exception e) {
            throw new DatabaseException("按时间查询文件失败");
        }
    }

    @Override
    public List<Files> queryAllFiles(long userId) {
        try {
            return filesMapper.queryAllFiles(userId);
        } catch (Exception e) {
            throw new DatabaseException("查询所有文件失败");
        }
    }

    @Override
    public List<Files> queryFileByType(String fileType, long userId) {
        try {
            return filesMapper.queryFileByType(fileType,userId);
        } catch (Exception e) {
            throw new DatabaseException("按文件类型查询文件失败");
        }
    }

    @Override
    public List<Files> queryFileByTypes(String[] fileTypes, long userId) {
        try {
            return filesMapper.queryFileByTypes(fileTypes, userId);
        } catch (Exception e) {
            throw new DatabaseException("按多种文件类型查询文件失败");
        }
    }
}
