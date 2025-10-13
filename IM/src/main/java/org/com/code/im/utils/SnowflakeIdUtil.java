package org.com.code.im.utils;

import org.com.code.im.pojo.SnowflakeIdWorker;

public class SnowflakeIdUtil {
    public static SnowflakeIdWorker userIdWorker = new SnowflakeIdWorker(0, 0);
    public static SnowflakeIdWorker messageIdWorker = new SnowflakeIdWorker(1, 0);
    public static SnowflakeIdWorker sessionIdWorker = new SnowflakeIdWorker(2, 0);
    public static SnowflakeIdWorker videoIdWorker = new SnowflakeIdWorker(3, 0);
    public static SnowflakeIdWorker videoCommentIdWorker = new SnowflakeIdWorker(5, 0);
    public static SnowflakeIdWorker postCommentIdWorker = new SnowflakeIdWorker(4, 0);
    public static SnowflakeIdWorker planIdWorker = new SnowflakeIdWorker(6, 0);
    public static SnowflakeIdWorker taskIdWorker = new SnowflakeIdWorker(7, 0);
    public static SnowflakeIdWorker postIdWorker = new SnowflakeIdWorker(8, 0);
    public static SnowflakeIdWorker fileIdWorker = new SnowflakeIdWorker(9, 0);
    public static SnowflakeIdWorker chatWindowIdWorker = new SnowflakeIdWorker(10, 0);
}