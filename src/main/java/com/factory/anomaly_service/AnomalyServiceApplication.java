package com.factory.anomaly_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.factory.anomaly_service")
@EnableJpaRepositories(basePackages = "com.factory.anomaly_service")
public class AnomalyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnomalyServiceApplication.class, args);
	}

}
