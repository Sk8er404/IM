package org.com.code.im.utils;

import java.time.*;

public class TimeConverter {
    public static LocalDateTime getMilliTime() {
        long timestampMillis = System.currentTimeMillis();
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMillis),
                ZoneId.of("Asia/Shanghai")
        );
    }
}
