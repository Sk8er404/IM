package org.com.code.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.com.code.im.pojo.Blocks;

import java.util.List;
import java.util.Map;

@Mapper
public interface BlockMapper {
    public void insertBlock(Map map);
    public int cancelBlock(Map map);
    public List<Blocks> queryBlockedUserList(long userId);
}
