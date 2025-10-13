package org.com.code.im.pojo.dto;

import lombok.*;
import org.com.code.im.pojo.query.CommentPageQuery;
import org.com.code.im.pojo.VideoComments;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageResponse {
    private List<VideoComments> commentList;
    private CommentPageQuery commentPageQuery;
} 