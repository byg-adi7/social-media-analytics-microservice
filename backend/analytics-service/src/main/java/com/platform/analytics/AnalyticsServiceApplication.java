package com.platform.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Analytics Service.
 * <p>
 * This microservice is responsible for connecting social media accounts,
 * synchronizing analytics data, aggregating metrics, and exposing
 * dashboard/report/chart-ready JSON data to the frontend.
 * <p>
 * This service does NOT perform local user authentication. All incoming
 * requests are validated against the central Auth Service via Feign.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.platform.analytics")
@EnableScheduling
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
