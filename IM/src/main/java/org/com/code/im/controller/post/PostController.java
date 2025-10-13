package org.com.code.im.controller.post;

import org.com.code.im.service.recorder.UserBehaviourRecorder;
import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.query.PostPageQuery;
import org.com.code.im.pojo.dto.PostPageResponse;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.pojo.enums.ActionType;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.post.PostService;
import org.com.code.im.service.learning.UserLearningProgressService;
import org.com.code.im.utils.MarkdownUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/post")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private MarkdownUtil markdownUtil;

    @Autowired
    private UserLearningProgressService userLearningProgressService;

    @Autowired
    private UserBehaviourRecorder userBehaviourRecorder;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    String checkIfPostIsValid(Posts posts) {
        String errorMessage =null;
        if(posts.getType()!= Posts.PostType.EXPERIENCE_SHARING&&posts.getType()!= Posts.PostType.QUESTION)
            errorMessage="非法的帖子类型,type只能是QUESTION或者EXPERIENCE_SHARING";
        if(posts.getTitle()==null||posts.getTitle().length()>50)
            errorMessage="帖子标题不能为空且长度不能超过50个字符";
        if(posts.getContent()==null||posts.getContent().isEmpty() || posts.getContent().length()>10000)
            errorMessage="帖子内容不能为空且长度不能超过10000个字符";
        if(posts.getTags()!=null&&posts.getTags().size()>10){
            errorMessage="帖子标签不能超过10个";
        }
        return errorMessage;
    }

    @PostMapping("/createPost")
    public ResponseHandler createPost(@RequestBody Posts posts) {
        Long userId = getCurrentUserId();
        String errorMessage = checkIfPostIsValid(posts);
        if(errorMessage != null)
            return new ResponseHandler(HttpStatus.BAD_REQUEST.value(), errorMessage);

        posts.setUserId(userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "帖子创建成功",postService.createPost(posts));
    }

    @PutMapping("/updatePost")
    public ResponseHandler updatePost(@RequestBody Posts posts) {
        Long userId = getCurrentUserId();
        if(posts.getId()==null)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"帖子的的id不能为空");

        if(posts.getType()!=null&&(posts.getType()== Posts.PostType.EXPERIENCE_SHARING||posts.getType()==  Posts.PostType.QUESTION))
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"修改帖子不能修改类型");

        if(posts.getTitle()!=null&&posts.getTitle().length()>50)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"帖子标题不能超过50个字符");

        if(posts.getContent()!=null&&posts.getContent().length()>10000)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"帖子内容不能超过10000个字符");

        if(posts.getTags()!=null&&posts.getTags().size()>10)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST,"帖子标签不能超过10个");

        posts.setUserId(userId);
        postService.updatePost(posts);
        return new ResponseHandler(ResponseHandler.SUCCESS, "更新成功");
    }

    @GetMapping("/getPostById/{postId}")
    public ResponseHandler getPostById(@PathVariable Long postId) {
        Posts post = postService.getPostById(postId);
        if (post == null) {
            return new ResponseHandler(ResponseHandler.NOT_FOUND, "未找到帖子");
        }

        /**
         * 记录帖子浏览量
         */
        Long userId = getCurrentUserId();
        userLearningProgressService.recordContentView(userId, postId, ContentType.POST);

        //记录帖子点击行为
        userBehaviourRecorder.recordAction(userId, ActionType.CLICK, ContentType.POST, postId);

        //记录用户看过哪些内容,在一段时间内,避免推荐重复的内容给用户
        userBehaviourRecorder.recordWhichContentUserHasViewed(userId, postId,ContentType.POST);
        
        if (post.getContent() != null && !post.getContent().isEmpty()) {
            String renderedContent = markdownUtil.renderToHtml(post.getContent());
            post.setContent(renderedContent);
        }
        
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", post);
    }

    @DeleteMapping("/deletePost/{postId}")
    public ResponseHandler deletePost(@PathVariable Long postId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ResponseHandler(HttpStatus.UNAUTHORIZED.value(), "未认证，请登录");
        }
        postService.deletePost(postId, userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "删除成功");
    }

    @GetMapping("/getPostsByKeyWord/{keyWord}")
    public ResponseHandler getPostsByHybridSearch(
            @PathVariable(name = "keyWord") String keyWord,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Posts> posts = postService.getPostsByHybridSearch(keyWord, page, size);

        //记录搜索关键词行为
        Long userId = getCurrentUserId();
        userBehaviourRecorder.recordSearchKeyword(userId, keyWord);

        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功",posts);
    }

    @GetMapping("/getPostsByTime")
    public ResponseHandler getPostsByTime(@RequestParam("startTime") String startTime,
                                          @RequestParam("endTime") String endTime,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size){

        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功",
                postService.searchPostByTime(startTime,endTime,page,size));
    }

    /**
     * 按帖子类型查询 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/getPostsByTypeWithCursor")
    public ResponseHandler getPostsByTypeWithCursor(@RequestBody PostPageQuery postPageQuery) {
        try {
            if (postPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            if (postPageQuery.getPostType() == null) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "帖子类型不能为空");
            }

            PostPageResponse postPageResponse = postService.getPostsByTypeWithCursor(postPageQuery);

            if (postPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多帖子");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", postPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 按用户ID查询帖子 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/getPostsByUserIdWithCursor")
    public ResponseHandler getPostsByUserIdWithCursor(@RequestBody PostPageQuery postPageQuery) {
        try {
            if (postPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            // 如果没有指定用户ID，则查询当前用户的帖子
            if (postPageQuery.getUserId() == null) {
                postPageQuery.setUserId(getCurrentUserId());
            }

            PostPageResponse postPageResponse = postService.getPostsByUserIdWithCursor(postPageQuery);

            if (postPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多帖子");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", postPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新帖子列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/queryLatestPostsWithCursor")
    public ResponseHandler queryLatestPostsWithCursor(@RequestBody PostPageQuery postPageQuery) {
        try {
            if (postPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            PostPageResponse postPageResponse = postService.queryLatestPostsWithCursor(postPageQuery);

            if (postPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多帖子");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", postPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门帖子列表 - 深度分页版本
     * 使用游标分页避免深度分页性能问题
     */
    @PostMapping("/queryMostViewedPostsWithCursor")
    public ResponseHandler queryMostViewedPostsWithCursor(@RequestBody PostPageQuery postPageQuery) {
        try {
            if (postPageQuery.getNextPage() == 0) {
                return new ResponseHandler(ResponseHandler.BAD_REQUEST, "翻页参数无效");
            }

            PostPageResponse postPageResponse = postService.queryMostViewedPostsWithCursor(postPageQuery);

            if (postPageResponse == null) {
                return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", "没有更多帖子");
            }
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", postPageResponse);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }
} 