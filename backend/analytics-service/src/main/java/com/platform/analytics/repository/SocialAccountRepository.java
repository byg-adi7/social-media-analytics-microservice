package com.platform.analytics.repository;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.SocialAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<SocialAccount> findAllByUserId(UUID userId, Pageable pageable);

    List<SocialAccount> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<SocialAccount> findByIdAndUserId(UUID id, UUID userId);

    Optional<SocialAccount> findByPlatformAndAccountId(Platform platform, String accountId);

    boolean existsByUserIdAndPlatformAndAccountId(UUID userId, Platform platform, String accountId);

    long countByUserIdAndActiveTrue(UUID userId);

    @Query("SELECT DISTINCT sa.platform FROM SocialAccount sa WHERE sa.userId = :userId AND sa.active = true")
    List<Platform> findDistinctActivePlatformsByUserId(@Param("userId") UUID userId);

    // CSV_IMPORT accounts are deliberately excluded: they have no live data
    // source to sync from, so the scheduled job resolving a
    // SocialMediaClient for one would either silently overwrite the user's
    // uploaded data with mock data, or (if a real client is registered for
    // that platform) fail against a fabricated account id/access token.
    @Query("SELECT sa FROM SocialAccount sa WHERE sa.active = true AND sa.connectionType = 'OAUTH'")
    List<SocialAccount> findAllActiveAccounts();

    // Caller must delete this user's Analytics rows first (FK), same as the
    // single-account disconnect flow in SocialAccountServiceImpl.
    void deleteAllByUserId(UUID userId);
}
