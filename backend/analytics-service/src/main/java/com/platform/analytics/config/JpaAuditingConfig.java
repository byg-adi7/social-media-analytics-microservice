package com.platform.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables automatic population of {@code createdAt} / {@code updatedAt}
 * fields declared in {@link com.platform.analytics.entity.BaseEntity}.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
