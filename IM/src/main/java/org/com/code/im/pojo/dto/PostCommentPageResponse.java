package org.com.code.im.pojo.dto;

import lombok.*;
import org.com.code.im.pojo.PostComment;
import org.com.code.im.pojo.query.PostCommentPageQuery;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentPageResponse {
    private List<PostComment> commentList;
    private PostCommentPageQuery commentPageQuery;
} 