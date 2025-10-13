package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlan {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    
    private String title;
    private String goal;
    /**
     * 状态：active、completed、cancelled
     */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 