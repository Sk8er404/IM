package org.com.code.im.pojo.enums;

import lombok.Getter;

@Getter
public enum ActionType {
    LIKE("like", 15),
    COMMENT("comment", 15),
    CLICK("click", 15),
    SEARCH_KEYWORD("search_keyword", 5);

    private final String type;
    private final long maxSize;

    ActionType(String type, long maxSize) {
        this.type = type;
        this.maxSize = maxSize;
    }
}
