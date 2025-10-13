package org.com.code.im.service.video;

import org.com.code.im.pojo.Videos;

import java.util.List;

public interface VideoLikeService {

    void insertVideoLike(long videoId,long userId);
    void deleteVideoLike(long videoId,long userId);
    List<Videos> queryLikedVideoList(long userId, int pageNum);
}
