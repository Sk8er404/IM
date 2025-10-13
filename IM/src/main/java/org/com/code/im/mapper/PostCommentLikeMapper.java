package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostCommentLikeMapper {
    public void insertPostCommentLike(List<Map<String,Long>> addList);
    public boolean checkIfUserAlreadyGiveLike(Map map);
    public void deletePostCommentLike(List<Map<String,Long>> deleteList);
    public void updatePostCommentLikes(List<Map<String,Long>> mapList);
}
