package org.com.code.im.pojo.dto;

import com.aliyun.oss.model.PartETag;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
/**
 * 这里我之所以用自定义的PartETagDTO类,而不用阿里云自带的PartETag类是因为
 * 我需要把携带有partNumber和ETag的对象列表放入redis中
 * 这个过程涉及到了redis序列化与反序列化，如果使用阿里云自带的PartETag类，还需要再写一个PartETagDTO类的反序列化器,不然会出问题
 * 所以我选择自定义一个PartETagDTO类,并且redis的GenericJackson2JsonRedisSerializer序列化器也支持我自定义的PartETagDTO类的序列化
 * 这样我就不用重写一个专门针对PartETag类的序列化器，而是直接使用GenericJackson2JsonRedisSerializer序列化器
 * 然后我还为PartETagDTO类写了一个toPartETag()方法,用于将PartETagDTO类转换为PartETag类,因为最终阿里云OSS合并文件时候需要用到PartETag类
 */
public class PartETagDTO {
    private int partNumber;
    private String eTag;

    // 转换为 PartETag
    public PartETag toPartETag() {
        return new PartETag(partNumber, eTag);
    }
}
