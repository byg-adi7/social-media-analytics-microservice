package com.platform.notification.repository;

import com.platform.notification.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findAllByUserIdOrderByGeneratedAtDesc(UUID userId);
    Optional<Report> findByIdAndUserId(UUID id, UUID userId);
}
