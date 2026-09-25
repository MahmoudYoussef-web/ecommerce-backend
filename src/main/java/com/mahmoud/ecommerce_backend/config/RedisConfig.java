package com.mahmoud.ecommerce_backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig implements org.springframework.cache.annotation.CachingConfigurer {

    /**
     * Unreadable cache entries (e.g. values written by an older payload shape)
     * must degrade to a MISS — the method re-executes and overwrites them —
     * never blow up the request with a 500. Security-relevant serialization
     * strictness itself is unchanged; this only controls failure handling.
     */
    @Override
    public org.springframework.cache.interceptor.CacheErrorHandler errorHandler() {
        return new org.springframework.cache.interceptor.SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception,
                                            org.springframework.cache.Cache cache, Object key) {
                org.slf4j.LoggerFactory.getLogger(RedisConfig.class)
                        .warn("Cache read failed — treating as miss | cache={} key={} error={}",
                                cache.getName(), key, exception.getMessage());
            }
        };
    }

    /**
     * Builds the cache ObjectMapper shared by production config and tests.
     *
     * Polymorphic default typing is kept ONLY because paginated results need
     * it, but it is constrained by an explicit allowlist validator: during
     * deserialization a "@class" tag may resolve exclusively to project DTO /
     * entity classes or Spring Data pagination support types. Any other type
     * (historical Jackson gadget classes such as
     * javax.management.remote.rmi.*, org.apache.* commons collections, etc.)
     * is rejected before instantiation.
     */
    public static ObjectMapper buildCacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new PageImplJacksonModule());

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.mahmoud.ecommerce_backend.")
                .allowIfSubType("org.springframework.data.domain.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();

        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        return mapper;
    }

    @Bean
    public org.springframework.cache.CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            org.springframework.core.env.Environment environment) {

        // Namespace cache keys by active profile(s): the dev server and the
        // integration-test JVMs share one Redis, and without isolation they
        // overwrite each other's entries (different databases behind the same
        // logical keys) — corrupting reads in BOTH directions.
        String profiles = String.join("-",
                environment.getActiveProfiles().length > 0
                        ? environment.getActiveProfiles()
                        : new String[]{"default"});
        String prefix = "ns:" + profiles + ":";

        RedisSerializationContext.SerializationPair<Object> serializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(buildCacheMapper())
                );

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(prefix)
                .serializeValuesWith(serializer)
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
