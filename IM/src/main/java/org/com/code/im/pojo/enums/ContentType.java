package org.com.code.im.pojo.enums;

import lombok.Getter;

/**
 * 学习内容类型枚举
 */
@Getter
public enum ContentType {
    POST("post"), VIDEO("video");

    private final String type;
    ContentType(String type) {
        this.type = type;
    }
} 