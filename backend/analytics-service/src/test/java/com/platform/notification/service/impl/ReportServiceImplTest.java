package com.platform.notification.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.PlatformMetricsResponse;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.service.AnalyticsQueryService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.constant.ReportStatus;
import com.platform.notification.constant.ReportType;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;
import com.platform.notification.entity.Report;
import com.platform.notification.repository.ReportRepository;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AnalyticsQueryService analyticsQueryService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void generate_platformComparison_buildsCsvAndFiresReadyNotification() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.PLATFORM_COMPARISON, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        PlatformMetricsResponse metrics = PlatformMetricsResponse.builder()
                .platform(Platform.YOUTUBE).followers(1000).views(5000).likes(200).comments(30)
                .shares(10).posts(5).engagementRate(4.5).growthRate(2.1).build();
        when(analyticsQueryService.getPlatformComparison(eq(userId), any(AnalyticsQueryRequest.class)))
                .thenReturn(List.of(metrics));
        when(reportRepository.save(any())).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReportResponse response = reportService.generate(userId, request);

        assertThat(response.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(response.getContent()).contains("YOUTUBE").contains("followers");
        verify(notificationService).create(eq(userId), eq(NotificationType.REPORT_READY), any());
    }

    @Test
    void generate_analyticsLookupFails_savesFailedReportAndThrows() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.SUMMARY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        when(analyticsQueryService.getSummary(eq(userId), any(AnalyticsQueryRequest.class)))
                .thenThrow(new RuntimeException("analytics lookup failed"));

        assertThatThrownBy(() -> reportService.generate(userId, request))
                .isInstanceOf(ExternalApiException.class);

        verify(reportRepository).save(argThatStatusIs(ReportStatus.FAILED));
        verify(notificationService, never()).create(any(), any(), any());
    }

    @Test
    void generate_endPeriodBeforeStartPeriod_throwsBadRequest() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.SUMMARY, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> reportService.generate(userId, request))
                .isInstanceOf(BadRequestException.class);

        verify(analyticsQueryService, never()).getSummary(any(), any());
    }

    @Test
    void getForUser_returnsMappedPagedSummaries() {
        Report report = Report.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .reportType(ReportType.PLATFORM_COMPARISON)
                .startPeriod(LocalDate.of(2026, 1, 1))
                .endPeriod(LocalDate.of(2026, 1, 31))
                .status(ReportStatus.COMPLETED)
                .generatedAt(LocalDateTime.now())
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(reportRepository.findAllByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));

        PagedResponse<ReportSummaryResponse> result = reportService.getForUser(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getById_throwsWhenNotFoundOrNotOwnedByUser() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getById(userId, reportId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Report argThatStatusIs(ReportStatus status) {
        return org.mockito.ArgumentMatchers.argThat(r -> r != null && r.getStatus() == status);
    }
}
