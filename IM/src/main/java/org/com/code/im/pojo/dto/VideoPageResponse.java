package org.com.code.im.pojo.dto;

import lombok.*;
import org.com.code.im.pojo.query.VideoPageQuery;
import org.com.code.im.pojo.Videos;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class VideoPageResponse {
    private List<Videos> videoList;
    private VideoPageQuery videoPageQuery;
} 