package com.platform.analytics.entity;

import com.platform.analytics.constant.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a social media account connected by a content creator (user).
 * <p>
 * A single user may connect multiple accounts across different platforms
 * (e.g. one YouTube channel, two Instagram accounts, one TikTok account).
 */
@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_platform_account_id",
                columnNames = {"platform", "account_id"}
        ),
        indexes = {
                @Index(name = "idx_social_account_user_id", columnList = "user_id"),
                @Index(name = "idx_social_account_platform", columnList = "platform")
        }
)
@Getter
@Setter
@ToString(exclude = {"accessToken", "refreshToken"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    /**
     * The unique account identifier as defined by the external platform.
     */
    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "username")
    private String username;

    @Column(name = "profile_image", length = 1000)
    private String profileImage;

    @Column(name = "access_token", length = 2000)
    private String accessToken;

    @Column(name = "refresh_token", length = 2000)
    private String refreshToken;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_synced")
    private LocalDateTime lastSynced;

    /**
     * Expiry timestamp of {@code accessToken}, used by real platform clients
     * (e.g. {@link com.platform.analytics.client.YouTubeSocialMediaClient})
     * to know when to proactively refresh via {@code refreshToken}. Null for
     * platforms/accounts using long-lived or mock tokens.
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;
}
