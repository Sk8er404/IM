package org.com.code.im.utils;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class GeoIpUtil {

    private static DatabaseReader reader=null;

    @Value("${app.data.geodb-path}")
    private String filePath; // 修改为非静态字段

    @PostConstruct
    public void init() {
        try {
            if (filePath == null || filePath.isEmpty()) {
                throw new IllegalArgumentException("GeoIP 数据库路径未配置");
            }
            File database = new File(filePath);
            reader = new DatabaseReader.Builder(database).build();
        } catch (Exception e) {
            throw new RuntimeException("GeoIP 数据库初始化失败", e);
        }
    }

    public static String getIpLocation(String ip) {
        try {
            // 检查是否为本地地址或私有地址
            if (isLocalOrPrivateAddress(ip)) {
                return "本地地址";
            }
            
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);

            Country country = response.getCountry();
            City city = response.getCity();

            return country.getName() + " " + city.getName();
        } catch (Exception e) {
            e.printStackTrace();
            return "未知归属地";
        }
    }

    /**
     * 检查是否为本地地址或私有地址
     */
    private static boolean isLocalOrPrivateAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        // IPv6 localhost
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        
        // IPv4 localhost
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
            return true;
        }
        
        // 私有IP地址段
        if (ip.startsWith("192.168.") || 
            ip.startsWith("10.") || 
            (ip.startsWith("172.") && isInRange172(ip))) {
            return true;
        }
        
        return false;
    }

    /**
     * 检查是否在172.16.0.0 - 172.31.255.255范围内
     */
    private static boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return false;
    }

    public static String getClientIpAddress(HttpServletRequest request) {
        /**
         * 优先从 X-Forwarded-For 头部获取真实 IP 地址
         */
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || ip.equalsIgnoreCase("unknown")) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || ip.equalsIgnoreCase("unknown")) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || ip.equalsIgnoreCase("unknown")) {
            ip = request.getRemoteAddr();
        }

        /**
         * 如果是多级代理，则取第一个非 unknown 的 IP
         */
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}