package org.com.code.im.pojo.enums;

import lombok.Getter;

import java.time.ZoneId;

@Getter
public enum TimeZoneConfig {
    SYSTEM_TIME_ZONE("Asia/Shanghai");

    private final String timeZone;
    private final ZoneId zoneId;
    // ← 缓存解析结果
    TimeZoneConfig(String timeZone) {
        this.timeZone = timeZone;
        zoneId = ZoneId.of(timeZone);
    }
}
