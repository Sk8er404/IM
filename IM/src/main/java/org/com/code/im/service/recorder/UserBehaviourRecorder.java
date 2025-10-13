package org.com.code.im.service.recorder;

import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.pojo.enums.ContentType;

import java.io.IOException;
import java.util.List;

public interface UserBehaviourRecorder {

    int Keywords_ExpiredTime_Days = 1;

    int WEIGHT_LIKE = 5;
    int WEIGHT_COMMENT = 3;
    int WEIGHT_CLICK = 1;
    int WEIGHT_SEARCH_KEYWORDS = 7;
    int MAX_VIEWED_CONTENT_SIZE = 150;

    // 获取用户行为数据时候使用的 key 格式
    String User_Action_ContentType = "user_%d:action_%s:contentType_%s";
    String User_Action = "user_%d:action_%s";
    String User_ViewedContentType = "user_%d:contentType_%s";

    void recordAction(Long userId, ActionType action, ContentType content, Long id);

    void deleteAction(Long userId, ActionType action, ContentType contentType, Long id);

    void recordSearchKeyword(Long userId, String keyword);

    List<Long> getActionIds(Long userId, ActionType action, ContentType contentType);

    float[] getWeightedAvgVector(Long userId, ContentType contentType) throws IOException;

    List<float[]> getSearchKeywordsVectors(Long userId);

    void recordWhichContentUserHasViewed(Long userId, Long videoId,ContentType contentType);

    List<Long> getWhichContentUserHasViewed(Long userId,ContentType contentType);
}