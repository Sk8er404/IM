package org.com.code.im.ElasticSearch.Service;

import java.util.List;
import java.util.Map;

public interface ESVideoService {
    /**
     * 延迟创建视频索引
     */
    void createVideoIndexDelayed(Map videoData);
    /**
     * 删除视频索引
     */
    void deleteVideoIndex(Long videoId);
    
    /**
     * 关键词搜索视频
     */
    List<Long> searchVideoByHybridSearch(String keyword, int page, int size);

    /**
     * 根据时间搜索视频
     */
    List<Long> searchVideosByTime(String startTime, String endTime, int page, int size);

}
