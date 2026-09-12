package com.servicedna.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public RedisCacheConfiguration cacheConfiguration(ObjectMapper objectMapper) {
    ObjectMapper cacheMapper = objectMapper.copy();
    PolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build();
    // EVERYTHING (not NON_FINAL): cached DTOs are Java records, which are implicitly final, so
    // NON_FINAL typing never embeds a type id for the record itself. On read, the deserializer
    // then has no class to target and falls back to a generic Object/Map, which chokes on any
    // nested typed collection (e.g. a record's `List<UUID>` field) with a "missing type id
    // property '@class'" SerializationException. EVERYTHING types finals too, so the record's
    // own class round-trips correctly.
    cacheMapper.activateDefaultTyping(
        ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(cacheMapper);
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .disableCachingNullValues()
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(serializer));
  }
}
