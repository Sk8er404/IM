package org.com.code.im.pojo.dto;

import lombok.*;
import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.query.PostPageQuery;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostPageResponse {
    private List<Posts> postList;
    private PostPageQuery postPageQuery;
} 