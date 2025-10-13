package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
public class Posts extends Likeable{

    //private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    private String userName;
    private PostType type; // Enum: EXPERIENCE_SHARING, QUESTION
    private String title;
    private String content;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long viewCount;

    //private long likeCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long commentCount;

    // 严格按照follow分页的方式，添加autoIncreasementId字段
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long autoIncreasementId;

    // Post Type 的枚举
    public enum PostType {
        EXPERIENCE_SHARING, // 学习心得
        QUESTION            // 问题
    }
} 