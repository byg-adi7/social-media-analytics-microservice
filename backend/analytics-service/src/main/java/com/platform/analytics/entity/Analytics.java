package com.platform.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single day's analytics snapshot for a connected social account.
 * <p>
 * One row is created per {@link SocialAccount} per {@code analyticsDate}. This
 * time-series structure is the foundation for trends, growth calculations,
 * and chart-ready datasets.
 */
@Entity
@Table(
        name = "analytics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_analytics_date",
                columnNames = {"social_account_id", "analytics_date"}
        ),
        indexes = {
                @Index(name = "idx_analytics_date", columnList = "analytics_date"),
                @Index(name = "idx_analytics_account_id", columnList = "social_account_id")
        }
)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_account_id", nullable = false)
    private SocialAccount socialAccount;

    @Column(name = "analytics_date", nullable = false)
    private LocalDate analyticsDate;

    @Column(name = "followers", nullable = false)
    private long followers;

    @Column(name = "following", nullable = false)
    private long following;

    @Column(name = "impressions", nullable = false)
    private long impressions;

    @Column(name = "reach", nullable = false)
    private long reach;

    @Column(name = "profile_visits", nullable = false)
    private long profileVisits;

    @Column(name = "views", nullable = false)
    private long views;

    @Column(name = "watch_time", nullable = false)
    private double watchTime;

    @Column(name = "likes", nullable = false)
    private long likes;

    @Column(name = "comments", nullable = false)
    private long comments;

    @Column(name = "shares", nullable = false)
    private long shares;

    @Column(name = "saves", nullable = false)
    private long saves;

    @Column(name = "posts", nullable = false)
    private long posts;

    @Column(name = "engagement_rate", nullable = false)
    private double engagementRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
