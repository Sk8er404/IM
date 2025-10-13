package org.com.code.im.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.AiConversation;

@Mapper
public interface ChatMemoryMapper {
    int createAiConversation(Map<String,Object> params);
    List<AiConversation> getAiConversation(Map<String,Object> params);
    String getAiConversationSummaryById(String conversationId);
    int deleteAiConversation(Map<String,Object> params);
    int updateAiConversation(Map<String,Object> params);
}
