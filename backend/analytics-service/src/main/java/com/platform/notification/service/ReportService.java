package com.platform.notification.service;

import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface ReportService {

    ReportResponse generate(UUID userId, CreateReportRequest request);

    List<ReportSummaryResponse> getForUser(UUID userId);

    ReportResponse getById(UUID userId, UUID reportId);
}
