CREATE DATABASE `im`

USE `im`

DROP TABLE `files`

DROP DATABASE `im`

CREATE TABLE `user` (
    id BIGINT PRIMARY KEY COMMENT '用户 ID（雪花算法）',
    userName VARCHAR(255) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
    `email` VARCHAR(255) NOT NULL,
    avatar VARCHAR(255) DEFAULT 'avatar' COMMENT '头像地址',
    bio TEXT COMMENT '个人简介',
    auth VARCHAR(15) COMMENT '用户权限',
    `locked` INT DEFAULT 0 NOT NULL COMMENT '账号是否被封，0为没被封，1为被封',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE INDEX unique_userName (userName),
    INDEX id_avatar_userName(id, avatar, userName)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE FOLLOWS (
    userId BIGINT NOT NULL COMMENT '用户ID',
    fanId BIGINT NOT NULL COMMENT '粉丝ID',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',

    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
    INDEX user_fan (userId, autoIncreasementId, fanId) COMMENT '覆盖索引，无需回表操作快速通过userId和autoIncrementId查询出fanId',
    PRIMARY KEY fan_user (fanId,userId) COMMENT '覆盖索引'
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE blocks (
    blockerId BIGINT NOT NULL COMMENT '屏蔽者ID',
    blockedId BIGINT NOT NULL COMMENT '被屏蔽者ID',
    blockedName VARCHAR(255) NOT NULL COMMENT '被屏蔽的人的名字',
    blockedAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '屏蔽时间',
    
    UNIQUE (blockerId,blockedId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
    messageId BIGINT PRIMARY KEY COMMENT '主键（雪花算法）',
    sessionId BIGINT NOT NULL,
    senderId BIGINT NOT NULL,
    content TEXT COMMENT '文本内容或媒体URL',
    messageType ENUM('text', 'image', 'file') DEFAULT 'text',
    createdAt DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '把消息的创建时间精度设置成毫秒级别',
    
    INDEX(createdAt,sessionId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 核心会话表（单聊和群聊共用）
CREATE TABLE `sessions` (
    sessionId BIGINT PRIMARY KEY COMMENT '会话ID（雪花算法）',
    sessionType ENUM('private', 'group') NOT NULL COMMENT '会话类型',
    -- 群聊特有字段（单聊时为空）
    groupAvatar VARCHAR(255) COMMENT '群头像(仅群聊有效)',
    groupName VARCHAR(100) COMMENT '群名称（仅群聊有效）',
    ownerId BIGINT COMMENT '群主ID（仅群聊有效）',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间'
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_members` (
   sessionId BIGINT NOT NULL COMMENT '会话ID',
   userId BIGINT NOT NULL COMMENT '用户ID',
   nickName VARCHAR(60) COMMENT '群昵称',
   joinedTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
   role ENUM('owner','admin', 'member') DEFAULT 'member' COMMENT '成员角色(群聊有效)',
   PRIMARY KEY (sessionId, userId),
   INDEX idx_userId (userId,sessionId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

drop TABLE `private_members`
CREATE TABLE `private_members` (
   sessionId BIGINT NOT NULL COMMENT '会话ID',
   userId1 BIGINT NOT NULL COMMENT '用户1 ID',
   userId2 BIGINT NOT NULL COMMENT '用户2 ID',
   INDEX (userId2,userId1,sessionId),
   unique (userId1,userId2,sessionId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE videos
CREATE TABLE videos (
    id BIGINT PRIMARY KEY COMMENT '视频 ID（雪花算法）',
    userId BIGINT NOT NULL COMMENT '投稿人 ID',
    userName VARCHAR(255) NOT NULL COMMENT '用户名',
    title VARCHAR(255) NOT NULL COMMENT '视频标题',
    url VARCHAR(255) NOT NULL COMMENT '视频地址',
    views BIGINT DEFAULT 0 COMMENT '播放量（Redis 缓存）',
    likeCount BIGINT DEFAULT 0 COMMENT '点赞量（Redis 缓存）',
    commentCount BIGINT DEFAULT 0 COMMENT '评论区留言数量',
    tags JSON COMMENT '视频标签',
    category VARCHAR(255) COMMENT '视频类别',
    duration DOUBLE COMMENT '视频时长（秒）',
    description VARCHAR(255) COMMENT '视频描述',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `status` ENUM('waitToReview', 'approved', 'rejected') DEFAULT 'approved' COMMENT '视频审核状态',
    reviewerId BIGINT COMMENT '审核员 ID',
    reviewedAt DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '审核时间',
    reviewNotes VARCHAR(255) COMMENT '审核备注',
    
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
    
    INDEX (userId,id),
    INDEX (createdAt),
    INDEX (`status`,views),
    INDEX (`status`,createdAt),
    UNIQUE (url),
    FULLTEXT (title, category, description)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE files
CREATE TABLE files (
    id BIGINT PRIMARY KEY COMMENT '文件 ID（雪花算法）',
    userId BIGINT NOT NULL,
    userName VARCHAR(255) NOT NULL COMMENT '用户名',
    fileName VARCHAR(255) NOT NULL COMMENT '文件名',
    filetype VARCHAR(255) NOT NULL COMMENT '文件后缀决定的文件类型',
    url VARCHAR(255) NOT NULL COMMENT '文件地址',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
    
    INDEX (userId),
    INDEX (createdAt),
    INDEX (filetype),
    UNIQUE (url),
    FULLTEXT (fileName)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE video_comments
CREATE TABLE video_comments (
    id BIGINT PRIMARY KEY COMMENT '评论 ID（雪花算法）',
    videoId BIGINT NOT NULL COMMENT '视频 ID',
    userId BIGINT NOT NULL COMMENT '评论者 ID',
    userName VARCHAR(255) NOT NULL,
    content TEXT NOT NULL COMMENT '评论内容',
    parentId BIGINT COMMENT '油管评论只有两层,假设评论A回复评论B,则这里的parentId是B的评论id',
    replyTo VARCHAR(255) COMMENT '假设评论A已经回复评论B,此时C又回复A在B底下的评论,则这里的replyComentId是A的名字,用于@',
    repliesCount BIGINT DEFAULT 0 COMMENT '回复数量',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
    
    INDEX (videoId, userId),
    INDEX (videoId, parentId),
    INDEX (id,userId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE video_likes (
    videoId BIGINT NOT NULL COMMENT '视频 ID',
    userId BIGINT NOT NULL COMMENT '点赞者 ID',
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE idxUserVideo (userId,videoId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE learning_plan
CREATE TABLE IF NOT EXISTS `learning_plan` (
  `id` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `goal` TEXT DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT 'ACTIVE',
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, 
  
   autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
   UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
    
  PRIMARY KEY (`id`),
  INDEX (`userId`),
  INDEX (`status`)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE learning_plan

CREATE TABLE IF NOT EXISTS `learning_task` (
  `id` BIGINT NOT NULL,
  `planId` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `description` TEXT NOT NULL,
  `frequency` VARCHAR(15) NOT NULL COMMENT '提醒频率: ONCE, DAILY, WEEKLY, MONTHLY',
   `targetDueDate` DATE COMMENT '目标截止日期',
  `reminderEnabled` BOOLEAN DEFAULT FALSE COMMENT '是否启用任务提醒',
  `reminderTime` TIME COMMENT '什么时间发送提醒消息',
  `isCompletedToday` BOOLEAN DEFAULT FALSE COMMENT "今天任务是否完成",
  `totalCompletions` INT DEFAULT 0 ,
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
   autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
   UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
   
  PRIMARY KEY (`id`),
  INDEX (`planId`),
  INDEX (`userId`),
  INDEX (`frequency`),
  INDEX (`targetDueDate`),
  INDEX (`reminderEnabled`, `reminderTime`),
  INDEX (`isCompletedToday`)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE posts

CREATE TABLE posts (
    id BIGINT PRIMARY KEY,
    userId BIGINT NOT NULL,
    userName VARCHAR(255) NOT NULL COMMENT '用户名',
    `type` VARCHAR(50) NOT NULL,                         -- 'EXPERIENCE_SHARING', 'QUESTION'
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    tags JSON,                                          -- Can store as JSON array string or comma-separated. Consider a separate join table for better tag management and searching.
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    viewCount BIGINT DEFAULT 0,
    likeCount BIGINT DEFAULT 0,
    commentCount BIGINT DEFAULT 0,
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
   
    INDEX idx_community_post_userId (userId),
    INDEX idx_community_post_type (TYPE),
    FULLTEXT (title,content)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_likes (
    postId BIGINT NOT NULL,
    userId BIGINT NOT NULL,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE (userId,postId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE post_comments

CREATE TABLE post_comments (
    id BIGINT PRIMARY KEY,
    postId BIGINT NOT NULL,
    userId BIGINT NOT NULL,
    userName VARCHAR(255) NOT NULL,
    parentId BIGINT COMMENT '油管评论只有两层,假设评论A回复评论B,则这里的parentId是B的评论id',
    replyTo VARCHAR(255) COMMENT '假设评论A已经回复评论B,此时C又回复A在B底下的评论,则这里的replyComentId是A的名字,用于@',
    content TEXT NOT NULL,
    likeCount BIGINT DEFAULT 0 NOT NULL,
    repliesCount BIGINT DEFAULT 0,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    autoIncreasementId BIGINT NOT NULL AUTO_INCREMENT COMMENT '游标分页查询的基准字段',
    UNIQUE KEY unique_autoIncreasementId (autoIncreasementId),
   
    INDEX idx_post_comment_postId (postId),             -- Index for quickly finding comments for a post
    INDEX idx_post_comment_userId (userId)              -- Index for finding comments by user
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_comment_likes (
    postId BIGINT NOT NULL COMMENT '对这个帖子底下的评论点赞',
    postCommentId BIGINT NOT NULL,
    userId BIGINT NOT NULL,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE (userId,postCommentId)
)ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE keyword_dict (
    keyword VARCHAR(512) PRIMARY KEY
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE user_learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId BIGINT NOT NULL,
    contentId BIGINT NOT NULL,
    contentType ENUM('POST', 'VIDEO') NOT NULL,
    firstAccessTimestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lastAccessTimestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    durationViewedSeconds INT DEFAULT 0,
     completionStatus ENUM('IN_PROGRESS', 'COMPLETED') DEFAULT 'IN_PROGRESS',
        
    INDEX idx_user_content (userId, contentId, contentType),
    INDEX idx_user_id (userId)
);

CREATE TABLE `ai_conversation_sessions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conversationId` VARCHAR(64) NOT NULL COMMENT '全局唯一的会话ID，建议使用UUID',
    `userId` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `title` VARCHAR(255) NOT NULL DEFAULT 'New Conversation' COMMENT '会话标题，可由LLM生成或取前几轮对话内容',
    `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
     PRIMARY KEY (`id`),
     UNIQUE KEY `uk_conversation_id` (`conversationId`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和AI对话会话元数据表';