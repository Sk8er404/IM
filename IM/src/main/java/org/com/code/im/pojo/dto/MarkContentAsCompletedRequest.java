package org.com.code.im.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.com.code.im.pojo.enums.ContentType;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MarkContentAsCompletedRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long contentId;
    private ContentType contentType;
} 