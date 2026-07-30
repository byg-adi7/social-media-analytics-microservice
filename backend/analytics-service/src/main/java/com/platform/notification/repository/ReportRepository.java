package com.platform.notification.repository;

import com.platform.notification.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    // Sort order comes from the Pageable itself (see ReportController's
    // @PageableDefault) rather than an OrderBy clause here, so there's one
    // source of truth instead of two competing sort specifications.
    Page<Report> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Report> findByIdAndUserId(UUID id, UUID userId);
    void deleteAllByUserId(UUID userId);
}
