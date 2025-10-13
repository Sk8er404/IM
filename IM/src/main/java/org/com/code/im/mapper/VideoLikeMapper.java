package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoLikeMapper{

    void insertVideoLike(List<Map<String,Long>> addList);
    boolean checkIfUserAlreadyGiveLike(Map map);
    void deleteVideoLike(List<Map<String,Long>> deleteList);
    List<Long> queryLikedVideoList(long userId);
    void updateVideoLikes(List<Map<String,Long>> mapList);
}
