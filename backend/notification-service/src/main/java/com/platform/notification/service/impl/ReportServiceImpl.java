package com.platform.notification.service.impl;

import com.platform.notification.client.AnalyticsServiceClient;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.constant.ReportStatus;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.AnalyticsSummary;
import com.platform.notification.dto.response.PlatformMetrics;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;
import com.platform.notification.entity.Report;
import com.platform.notification.exception.BadRequestException;
import com.platform.notification.exception.ExternalApiException;
import com.platform.notification.exception.ResourceNotFoundException;
import com.platform.notification.repository.ReportRepository;
import com.platform.notification.service.NotificationService;
import com.platform.notification.service.ReportService;
import com.platform.notification.util.CsvReportFormatter;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final NotificationService notificationService;

    /**
     * Deliberately not @Transactional: on a failed Analytics Service call,
     * the FAILED report row below must still persist even though this
     * method itself then throws - wrapping the whole method in one
     * transaction would roll that save back along with the exception.
     * Each repository.save() already commits in its own transaction via
     * Spring Data JPA's repository proxy.
     */
    @Override
    public ReportResponse generate(UUID userId, String bearerToken, CreateReportRequest request) {
        if (request.getEndPeriod().isBefore(request.getStartPeriod())) {
            throw new BadRequestException("endPeriod must not be before startPeriod");
        }

        String csv;
        try {
            csv = switch (request.getReportType()) {
                case PLATFORM_COMPARISON -> {
                    List<PlatformMetrics> metrics = analyticsServiceClient.getPlatformComparison(
                            bearerToken, request.getStartPeriod(), request.getEndPeriod());
                    yield CsvReportFormatter.forPlatformComparison(metrics);
                }
                case SUMMARY -> {
                    AnalyticsSummary summary = analyticsServiceClient.getSummary(
                            bearerToken, request.getStartPeriod(), request.getEndPeriod());
                    yield CsvReportFormatter.forSummary(summary);
                }
            };
        } catch (FeignException ex) {
            log.error("Analytics Service call failed while generating {} report for user {}: HTTP {}",
                    request.getReportType(), userId, ex.status());
            reportRepository.save(Report.builder()
                    .userId(userId)
                    .reportType(request.getReportType())
                    .startPeriod(request.getStartPeriod())
                    .endPeriod(request.getEndPeriod())
                    .status(ReportStatus.FAILED)
                    .errorMessage("Failed to fetch analytics data")
                    .generatedAt(LocalDateTime.now())
                    .build());
            throw new ExternalApiException("Could not generate report: analytics data unavailable");
        }

        Report saved = reportRepository.save(Report.builder()
                .userId(userId)
                .reportType(request.getReportType())
                .startPeriod(request.getStartPeriod())
                .endPeriod(request.getEndPeriod())
                .status(ReportStatus.COMPLETED)
                .content(csv)
                .generatedAt(LocalDateTime.now())
                .build());

        log.info("Generated {} report {} for user {}", request.getReportType(), saved.getId(), userId);

        notificationService.create(userId, NotificationType.REPORT_READY,
                "Your " + request.getReportType() + " report for " + request.getStartPeriod()
                        + " to " + request.getEndPeriod() + " is ready.");

        return toResponse(saved);
    }

    @Override
    public List<ReportSummaryResponse> getForUser(UUID userId) {
        return reportRepository.findAllByUserIdOrderByGeneratedAtDesc(userId).stream()
                .map(r -> ReportSummaryResponse.builder()
                        .id(r.getId())
                        .reportType(r.getReportType())
                        .startPeriod(r.getStartPeriod())
                        .endPeriod(r.getEndPeriod())
                        .status(r.getStatus())
                        .generatedAt(r.getGeneratedAt())
                        .build())
                .toList();
    }

    @Override
    public ReportResponse getById(UUID userId, UUID reportId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Report", reportId));
        return toResponse(report);
    }

    private ReportResponse toResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .reportType(report.getReportType())
                .startPeriod(report.getStartPeriod())
                .endPeriod(report.getEndPeriod())
                .status(report.getStatus())
                .content(report.getContent())
                .errorMessage(report.getErrorMessage())
                .generatedAt(report.getGeneratedAt())
                .build();
    }
}
