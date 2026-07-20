package com.platform.notification.service.impl;

import com.platform.notification.client.AnalyticsServiceClient;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.constant.ReportStatus;
import com.platform.notification.constant.ReportType;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.AnalyticsPlatform;
import com.platform.notification.dto.response.PlatformMetrics;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.entity.Report;
import com.platform.notification.exception.BadRequestException;
import com.platform.notification.exception.ExternalApiException;
import com.platform.notification.exception.ResourceNotFoundException;
import com.platform.notification.repository.ReportRepository;
import com.platform.notification.service.NotificationService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
    private AnalyticsServiceClient analyticsServiceClient;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UUID userId;
    private String bearerToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bearerToken = "Bearer test-token";
    }

    @Test
    void generate_platformComparison_buildsCsvAndFiresReadyNotification() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.PLATFORM_COMPARISON, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        PlatformMetrics metrics = new PlatformMetrics(
                AnalyticsPlatform.YOUTUBE, 1000, 5000, 200, 30, 10, 5, 4.5, 2.1);
        when(analyticsServiceClient.getPlatformComparison(
                bearerToken, request.getStartPeriod().toString(), request.getEndPeriod().toString()))
                .thenReturn(List.of(metrics));
        when(reportRepository.save(any())).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReportResponse response = reportService.generate(userId, bearerToken, request);

        assertThat(response.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(response.getContent()).contains("YOUTUBE").contains("followers");
        verify(notificationService).create(eq(userId), eq(NotificationType.REPORT_READY), any());
    }

    @Test
    void generate_analyticsServiceUnreachable_savesFailedReportAndThrows() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.SUMMARY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        Request feignRequest = Request.create(Request.HttpMethod.GET, "/api/analytics/summary",
                java.util.Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(analyticsServiceClient.getSummary(
                bearerToken, request.getStartPeriod().toString(), request.getEndPeriod().toString()))
                .thenThrow(new FeignException.ServiceUnavailable("unavailable", feignRequest, null, null));

        assertThatThrownBy(() -> reportService.generate(userId, bearerToken, request))
                .isInstanceOf(ExternalApiException.class);

        verify(reportRepository).save(argThatStatusIs(ReportStatus.FAILED));
        verify(notificationService, never()).create(any(), any(), any());
    }

    @Test
    void generate_endPeriodBeforeStartPeriod_throwsBadRequest() {
        CreateReportRequest request = new CreateReportRequest(
                ReportType.SUMMARY, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> reportService.generate(userId, bearerToken, request))
                .isInstanceOf(BadRequestException.class);

        verify(analyticsServiceClient, never()).getSummary(any(), any(), any());
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
