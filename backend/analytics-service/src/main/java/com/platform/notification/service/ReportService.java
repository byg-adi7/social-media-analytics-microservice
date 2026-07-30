package com.platform.notification.service;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportService {

    ReportResponse generate(UUID userId, CreateReportRequest request);

    PagedResponse<ReportSummaryResponse> getForUser(UUID userId, Pageable pageable);

    ReportResponse getById(UUID userId, UUID reportId);
}
