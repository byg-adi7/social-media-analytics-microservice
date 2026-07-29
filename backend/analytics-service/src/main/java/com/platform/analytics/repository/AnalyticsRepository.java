package com.platform.analytics.repository;

import com.platform.analytics.entity.Analytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Analytics} time-series persistence and aggregation
 * queries used by dashboard, report, trend and chart endpoints.
 */
@Repository
public interface AnalyticsRepository extends JpaRepository<Analytics, UUID> {

    List<Analytics> findBySocialAccountIdOrderByAnalyticsDateAsc(UUID socialAccountId);

    Optional<Analytics> findBySocialAccountIdAndAnalyticsDate(UUID socialAccountId, LocalDate analyticsDate);

    List<Analytics> findBySocialAccountIdAndAnalyticsDateBetweenOrderByAnalyticsDateAsc(
            UUID socialAccountId, LocalDate startDate, LocalDate endDate);

    // JOIN FETCH a.socialAccount below is load-bearing, not an optimization:
    // every caller of these methods eventually reads a.getSocialAccount()
    // (e.g. to group by platform) after this method returns. socialAccount
    // is a LAZY @ManyToOne and open-in-view is disabled, so without the
    // fetch join that access throws LazyInitializationException as soon as
    // any row is returned - see the fix for this exact class of bug.
    @Query("""
            SELECT a FROM Analytics a
            JOIN FETCH a.socialAccount
            WHERE a.socialAccount.userId = :userId
            AND a.analyticsDate BETWEEN :startDate AND :endDate
            ORDER BY a.analyticsDate ASC
            """)
    List<Analytics> findAllByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT a FROM Analytics a
            JOIN FETCH a.socialAccount
            WHERE a.socialAccount.userId = :userId
            AND a.socialAccount.platform = :platform
            AND a.analyticsDate BETWEEN :startDate AND :endDate
            ORDER BY a.analyticsDate ASC
            """)
    List<Analytics> findAllByUserIdAndPlatformAndDateRange(
            @Param("userId") UUID userId,
            @Param("platform") com.platform.analytics.constant.Platform platform,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT a FROM Analytics a
            JOIN FETCH a.socialAccount
            WHERE a.socialAccount.id = :socialAccountId
            ORDER BY a.analyticsDate DESC
            LIMIT 1
            """)
    Optional<Analytics> findLatestBySocialAccountId(@Param("socialAccountId") UUID socialAccountId);

    @Query("""
            SELECT a FROM Analytics a
            JOIN FETCH a.socialAccount
            WHERE a.socialAccount.userId = :userId
            ORDER BY a.analyticsDate DESC
            """)
    List<Analytics> findAllByUserIdOrderByDateDesc(@Param("userId") UUID userId);

    void deleteBySocialAccountId(UUID socialAccountId);
}
