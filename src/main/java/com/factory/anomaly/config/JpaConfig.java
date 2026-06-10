package com.factory.anomaly.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = "com.factory.anomaly")
@EntityScan(basePackages = "com.factory.anomaly")
@EnableJpaAuditing
public class JpaConfig {

}
