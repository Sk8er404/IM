package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Files {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long userId;

    private String userName;
    private String title;//如果是视频,这里是视频标题
    private String fileName;//如果普通是非视频文件,则这里是文件名
    private String fileType;
    private String url;//视频和非视频文件共同享有的url
    private String duration;//视频享有的时长变量
    private List<String> tags;//视频的标签
    private String category;//视频的分类
    private String description;//视频的描述
    private LocalDateTime createdAt;
}
