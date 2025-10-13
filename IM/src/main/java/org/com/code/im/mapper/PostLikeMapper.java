package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostLikeMapper {
    public void insertPostLike(List<Map<String,Long>> addList);
    public boolean checkIfUserAlreadyGiveLike(Map map);
    public void deletePostLike(List<Map<String,Long>> deleteList);
    public List<Long> queryLikedPostList(long userId);
    public void updatePostLikes(List<Map<String,Long>> mapList);
}
