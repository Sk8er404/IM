package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostComment extends Likeable{

    //private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    
    private String userName;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;
    
    private String replyTo;
    private String content;
    //private Long likeCount;
    private Long repliesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long autoIncreasementId;
} 