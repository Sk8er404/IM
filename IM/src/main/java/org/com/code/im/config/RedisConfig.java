package org.com.code.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 创建通用的 RedisTemplate 配置方法
     */
    private <K, V> RedisTemplate<K, V> createRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            RedisSerializer<K> keySerializer,
            RedisSerializer<V> valueSerializer) {
        RedisTemplate<K, V> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 设置 key 和 hashKey 的序列化方式
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setHashKeySerializer(keySerializer);

        // 设置 value 和 hashValue 的序列化方式
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);

        // 初始化 RedisTemplate
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    @Bean("redisTemplateLong")
    @Primary
    public RedisTemplate<String, Long> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(
                redisConnectionFactory,
                new StringRedisSerializer(),
                new GenericToStringSerializer<>(Long.class)
        );
    }

    @Bean("strRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(
                redisConnectionFactory,
                new StringRedisSerializer(),
                new StringRedisSerializer()
        );
    }
    @Bean("objRedisTemplate")
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(
                redisConnectionFactory,
                new StringRedisSerializer(),
                /**
                 * 如果一开始知道有这个序列化器，我就不用以上的序列化器了，但是现在已经是项目后期，改动太麻烦了
                 *
                 * GenericJackson2JsonRedisSerializer 的工作原理涉及到将复杂对象转换为 JSON 字符串，
                 * 然后再通过某种方式（例如 StringRedisSerializer）将其序列化为字节信息存储在 Redis 中，
                 * 反序列化过程则相反，先将字节信息反序列化为字符串，再通过 JSON 解析器将字符串转换为对象。
                 */
                new GenericJackson2JsonRedisSerializer()
        );
    }

    /**
     * 专门用于存储和读取二进制数据（如序列化后的向量）的RedisTemplate
     */
    @Bean("redisTemplateByteArray")
    public RedisTemplate<String, byte[]> redisTemplateByteArray(RedisConnectionFactory redisConnectionFactory) {

        return createRedisTemplate(
                redisConnectionFactory,
                new StringRedisSerializer(),
                RedisSerializer.byteArray()
        );
    }
}
