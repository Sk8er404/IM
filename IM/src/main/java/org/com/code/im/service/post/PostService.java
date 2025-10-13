package org.com.code.im.service.post;

import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.query.PostPageQuery;
import org.com.code.im.pojo.dto.PostPageResponse;

import java.util.List;

public interface PostService {
    Posts createPost(Posts post);

    void updatePost(Posts existingPost);

    void deletePost(Long postId, Long userId);

    Posts getPostById(Long postId);

    List<Posts> getPostsByHybridSearch(String keyWord, int page, int size);

    List<Posts> searchPostByTime(String startTime, String endTime, int page, int size);

    PostPageResponse getPostsByTypeWithCursor(PostPageQuery postPageQuery);

    PostPageResponse getPostsByUserIdWithCursor(PostPageQuery postPageQuery);

    PostPageResponse queryLatestPostsWithCursor(PostPageQuery postPageQuery);

    PostPageResponse queryMostViewedPostsWithCursor(PostPageQuery postPageQuery);
}
