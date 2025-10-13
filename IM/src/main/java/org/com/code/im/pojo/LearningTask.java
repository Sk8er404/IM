package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LearningTask {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long planId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    
    private String description;
    private String frequency; // "ONCE", "DAILY", "WEEKLY", "MONTHLY"
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDueDate;
    
    private boolean reminderEnabled = false;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime reminderTime;

    private boolean isCompletedToday = false;
    private int totalCompletions = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean getReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

} 