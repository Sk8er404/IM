package org.com.code.im.controller.post;

import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.utils.MarkdownUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/markdown")
public class MarkdownController {

    @Autowired
    private MarkdownUtil markdownUtil;

    @PostMapping("/render")
    public ResponseHandler renderMarkdown(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");
            if (content == null) {
                return new ResponseHandler(HttpStatus.BAD_REQUEST.value(), "内容不能为空");
            }
            
            String htmlContent = markdownUtil.renderToHtml(content);
            return new ResponseHandler(ResponseHandler.SUCCESS, "渲染成功", htmlContent);
        } catch (Exception e) {
            return new ResponseHandler(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Markdown渲染失败: " + e.getMessage());
        }
    }
} 