package com.factory.anomaly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    private final String host;
    private final int port;
    private final String password;
    private final boolean sslEnabled;

    public RedisConfig(@Value("${spring.data.redis.host}") String host,
        @Value("${spring.data.redis.port}") int port,
        @Value("${spring.data.redis.password:}") String password,
        @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.sslEnabled = sslEnabled;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setPassword(RedisPassword.of(password));

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
            LettuceClientConfiguration.builder();
        if (sslEnabled) {
            builder.useSsl().disablePeerVerification();
        }
        return new LettuceConnectionFactory(config, builder.build());
    }
}
