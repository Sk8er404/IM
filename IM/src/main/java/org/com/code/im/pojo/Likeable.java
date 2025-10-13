package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Likeable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected Long likeCount;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected Long id;

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getLikeCount() {
        return likeCount;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
