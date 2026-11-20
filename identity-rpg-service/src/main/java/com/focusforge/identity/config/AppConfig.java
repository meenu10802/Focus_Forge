package com.focusforge.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.identity.profile.DashboardDto;
import com.focusforge.identity.profile.ProfileService;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Typed JSON serializer for the dashboard cache: readable in redis-cli and
     * no polymorphic type metadata to trust on deserialization.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer dashboardCache(ObjectMapper objectMapper, RpgProperties props) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, DashboardDto.class);
        return builder -> builder.withCacheConfiguration(ProfileService.DASHBOARD_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .prefixCacheNameWith("identity:")
                        .entryTtl(props.dashboardCacheTtl())
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
