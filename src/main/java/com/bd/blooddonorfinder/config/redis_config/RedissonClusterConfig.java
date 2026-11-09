/*
package com.bd.blooddonorfinder.config.redis_config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedissonClusterConfig {
    @Value("#{'${redis.cluster.hosts}'.split(',')}")
    private String[] nodes;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress(nodes)
                .setMasterConnectionPoolSize(500)
                .setSlaveConnectionPoolSize(500)
                .setMasterConnectionMinimumIdleSize(16)
                .setSlaveConnectionMinimumIdleSize(16)
                .setSubscriptionConnectionPoolSize(128)
                .setTimeout(15_000)
                .setIdleConnectionTimeout(30_000);
        return Redisson.create(config);
    }

    @Bean
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redissonClient){
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean("redissonTemplate")
    public RedisTemplate<String, Object> redissonTemplate(@Qualifier("redissonConnectionFactory") RedissonConnectionFactory connectionFactory,
                                                          ObjectMapper objectMapper){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(serializer);
        return template;

    }

    @Bean("redissonIntegerTemplate")
    public RedisTemplate<String, Integer> redissonIntegerTemplate(@Qualifier("redissonConnectionFactory") RedissonConnectionFactory connectionFactory){
        RedisTemplate<String, Integer> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

*/
/*    @Bean("redissonStringTemplate")
    public StringRedisTemplate redissonStringTemplate(
            @Qualifier("redissonConnectionFactory") RedissonConnectionFactory factory) {

        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }*//*



















}
*/
