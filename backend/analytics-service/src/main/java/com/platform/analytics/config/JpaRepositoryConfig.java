package com.platform.analytics.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * com.platform.notification is a sibling package, not nested under
 * com.platform.analytics - Spring Data JPA's repository/entity scanning
 * doesn't follow AnalyticsServiceApplication's scanBasePackages (it follows
 * @AutoConfigurationPackage, which is always just that class's own
 * package), so both packages must be listed explicitly here.
 * <p>
 * Deliberately a separate @Configuration class, not annotations directly on
 * AnalyticsServiceApplication: a @WebMvcTest slice uses that class as its
 * @SpringBootConfiguration but sets up no datasource at all, and explicit
 * @EnableJpaRepositories/@EntityScan there would be processed anyway
 * (unlike excluded autoconfiguration), breaking every controller-slice
 * test. As a plain @Configuration, @WebMvcTest's narrowed scan skips it
 * exactly like it already skips JpaAuditingConfig.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.platform.analytics.repository", "com.platform.notification.repository"})
@EntityScan(basePackages = {"com.platform.analytics.entity", "com.platform.notification.entity"})
public class JpaRepositoryConfig {
}
