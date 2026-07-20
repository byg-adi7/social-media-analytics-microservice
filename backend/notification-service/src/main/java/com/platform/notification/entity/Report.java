package com.platform.notification.entity;

import com.platform.notification.constant.ReportStatus;
import com.platform.notification.constant.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A generated report, stored inline as CSV text. userId is a plain UUID
 * column (see Notification's class comment for why there's no cross-service
 * foreign key).
 */
@Entity
@Table(
        name = "reports",
        indexes = @Index(name = "idx_report_user_id", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Column(name = "start_period", nullable = false)
    private LocalDate startPeriod;

    @Column(name = "end_period", nullable = false)
    private LocalDate endPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    // Deliberately not @Lob: on PostgreSQL, Hibernate maps a @Lob String to
    // the oid/Large Object type, which requires streaming the value inside
    // an active transaction - reading it back afterward (e.g. a plain list
    // query with no transaction boundary) throws
    // "JpaSystemException: Unable to access lob stream". A plain TEXT
    // column has none of that ceremony and is the standard way to store
    // CSV-sized text.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;
}
