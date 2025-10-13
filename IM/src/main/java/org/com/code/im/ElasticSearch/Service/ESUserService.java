package org.com.code.im.ElasticSearch.Service;

import java.util.List;
import java.util.Map;

public interface ESUserService {
    void createUserIndex(Map userMap);
    /**
     * 删除索引
     */
    void deleteUserIndex(Long userId);
    /**
     * 更新索引中的文档
     */
    void updateUserIndex(Map userMap);

    /**
     *  根据用户名模糊搜索用户
     */
    List<Long> searchUserByName(String userName, int page, int size);
}
