package com.platform.analytics.repository;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SocialAccount} persistence operations.
 */
@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findAllByUserId(UUID userId);

    List<SocialAccount> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<SocialAccount> findByIdAndUserId(UUID id, UUID userId);

    Optional<SocialAccount> findByUserIdAndPlatformAndAccountId(UUID userId, Platform platform, String accountId);

    boolean existsByUserIdAndPlatformAndAccountId(UUID userId, Platform platform, String accountId);

    long countByUserIdAndActiveTrue(UUID userId);

    @Query("SELECT DISTINCT sa.platform FROM SocialAccount sa WHERE sa.userId = :userId AND sa.active = true")
    List<Platform> findDistinctActivePlatformsByUserId(@Param("userId") UUID userId);

    @Query("SELECT sa FROM SocialAccount sa WHERE sa.active = true")
    List<SocialAccount> findAllActiveAccounts();
}
