package org.com.code.im.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.com.code.im.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class JWTUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecretKey;

    private static String JWT_SECRET;

    @PostConstruct
    public void initFilePath() {
        JWT_SECRET = jwtSecretKey;
    }

    private static RedisTemplate<String, Long> redisTemplate;

    /**
     * token 过期时间，单位毫秒
     */
    private static final long EXPIRE_TIME_Millis = Duration.ofDays(7).getSeconds()*1000;
    /**
     * 设置一个账号最多同时在线的设备数量,一旦登录设备超过这个数量,
     * 那么就会挤掉之前最先登录的设备,挤掉的操作是:
     * 1. 最先登录的设备的token值被强制从Redis中删除,意味着SpringBoot的网页服务不可用,也不能通过netty的token身份验证建立连接
     * 2. 同时正在连接的websocket channel 会被立马强制关闭
     */
    @Value("${app.maxOnlineNumber}")
    private long maxOnlineNumber;

    private static long MAX_ONLINE_NUMBER;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        MAX_ONLINE_NUMBER=maxOnlineNumber;
    }

    public JWTUtils(@Qualifier("redisTemplateLong") RedisTemplate<String, Long> redisTemplate) {
        JWTUtils.redisTemplate = redisTemplate;
    }

    public static long getMaxOnlineNumber(){
        return MAX_ONLINE_NUMBER;
    }

    public static String getJwtToken(long userId){

        String authentication = "Auth"+userId;

        Map<Object, Object> tokenMap = redisTemplate.opsForHash().entries(authentication);
        if(tokenMap.size()== MAX_ONLINE_NUMBER){
            long minKeyValue = Long.MAX_VALUE;
            String keyOfMinKey=null;
            for(Object key:tokenMap.keySet()){
                if((long)tokenMap.get(key)<minKeyValue){
                    minKeyValue = (long)tokenMap.get(key);
                    keyOfMinKey = (String) key;
                }
            }
            redisTemplate.opsForHash().delete(authentication,keyOfMinKey);
        }

        StringBuilder jwtSecretBuffer= new StringBuilder();
        Map<String, String> claimsOfRandomDigit= new HashMap<>();

        String random = String.valueOf(System.currentTimeMillis());
        claimsOfRandomDigit.put("random", random);
        String token = Jwts.builder()
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET)
                .setClaims(claimsOfRandomDigit)
                .compact();

        redisTemplate.opsForHash().put(authentication,token,System.currentTimeMillis());

        jwtSecretBuffer.append(authentication).append(" ").append(token);
        return jwtSecretBuffer.toString();
    }

    public static Long checkToken(String authToken) {
        if(authToken==null)
            throw new ResourceNotFoundException("未携带token");

        String[] parts = authToken.split(" ");
        if (parts.length != 2) {
            throw new ResourceNotFoundException("身份验证失败，token格式无效");
        }

        Object timestamp =redisTemplate.opsForHash().get(parts[0], parts[1]);
        if (timestamp != null) {
            long delta = System.currentTimeMillis() - (long) timestamp;
            if (delta > EXPIRE_TIME_Millis){
                redisTemplate.opsForHash().delete(parts[0], parts[1]);
                throw new ResourceNotFoundException("身份验证失败，token已过期,重新登录");
            }
            return Long.parseLong(parts[0].replace("Auth",""));
        } else {
            throw new ResourceNotFoundException("token无效，请登录 (最多2个设备同时在线,你可能被强行挤下线)");
        }
    }

    public static void deleteToken(String token) {
        String[] parts = token.split(" ");
        if (parts.length != 2) {
            throw new ResourceNotFoundException("token格式无效,登出失败");
        }
        try {
            redisTemplate.opsForHash().delete(parts[0], parts[1]);
        } catch (Exception e) {
            throw new ResourceNotFoundException("登出失败");
        }
    }
}
