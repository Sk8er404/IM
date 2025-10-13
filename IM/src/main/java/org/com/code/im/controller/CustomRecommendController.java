package org.com.code.im.controller;

import org.com.code.im.pojo.Posts;
import org.com.code.im.pojo.Videos;
import org.com.code.im.pojo.enums.ContentType;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.customRecommend.CustomRecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CustomRecommendController {
    @Autowired
    CustomRecommendService customRecommendService;

    private static final Set<String> CONTENT_TYPES =
            Arrays.stream(ContentType.values())
                    .map(ContentType::getType)  // ← 关键：用你自己的 type 字段
                    .collect(Collectors.toSet());

    @GetMapping("/api/recommendedPosts")
    public ResponseHandler getRecommendedPost(@RequestParam int number) {
        if(number <= 0|| number > 20)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "推荐帖子数量限制在1~20", null);
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Posts> getRecommendedPost = customRecommendService.getRecommendedPosts(userId,number);
        return new ResponseHandler(ResponseHandler.SUCCESS, "成功获取推荐帖子", getRecommendedPost);
    }

    @GetMapping("/api/recommendedVideos")
    public ResponseHandler getRecommendedVideo(@RequestParam int number) {
        if(number <= 0|| number > 20)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "推荐视频数量限制在1~20", null);
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Videos> getRecommendedVideos = customRecommendService.getRecommendedVideos(userId,number);
        return new ResponseHandler(ResponseHandler.SUCCESS, "成功获取推荐视频", getRecommendedVideos);
    }

    @GetMapping("/api/similarPosts")
    public ResponseHandler getSimilarPosts(@RequestParam Long id,@RequestParam int number) {
        if(number <= 0|| number > 20)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "推荐相似帖子数量限制在1~20", null);

        List<Posts> getSimilarPosts = customRecommendService.getSimilarPosts(id, number);
        return new ResponseHandler(ResponseHandler.SUCCESS, "成功获取相似帖子", getSimilarPosts);
    }

    @GetMapping("/api/similarVideos")
    public ResponseHandler getSimilarVideos(@RequestParam Long id,@RequestParam int number) {
        if(number <= 0|| number > 20)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "推荐相似视频数量限制在1~20", null);

        List<Videos> getSimilarVideos = customRecommendService.getSimilarVideos(id, number);
        return new ResponseHandler(ResponseHandler.SUCCESS, "成功获取相似视频", getSimilarVideos);
    }

}
