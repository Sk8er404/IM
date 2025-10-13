package org.com.code.im.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
//这个类专门用来映射Elasticsearch中包含向量的文档。
@JsonIgnoreProperties(ignoreUnknown = true)
//它告诉JSON解析器，如果ES文档中有多余的字段，请直接忽略，不要报错。
public class ContentParentIdDoc {
    private Long contentParentId;
}
