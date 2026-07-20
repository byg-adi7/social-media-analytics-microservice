package com.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the Notification Service.
 * <p>
 * Owns two features: in-app notifications (created either by end users'
 * own actions surfaced elsewhere, or internally by other services such as
 * the Analytics Service) and on-demand analytics reports (pulled live from
 * the Analytics Service and rendered as CSV).
 * <p>
 * This service does NOT perform local user authentication. All incoming
 * user-facing requests are validated against the central Auth Service via
 * Feign, exactly like the Analytics Service.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.platform.notification")
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
