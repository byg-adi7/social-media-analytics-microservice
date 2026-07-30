package com.platform.notification.controller;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.exception.BadRequestException;
import com.platform.notification.constant.ReportStatus;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.notification.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "On-demand analytics reports, generated from live Analytics Service data")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a report for a date range, pulling real data from the Analytics Service")
    public ReportResponse generate(@Valid @RequestBody CreateReportRequest request) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Generating {} report for user {}", request.getReportType(), userId);
        return reportService.generate(userId, request);
    }

    @GetMapping
    @Operation(summary = "List the current user's generated reports, most recent first",
            description = "Paginated: pass page/size query params (defaults: page=0, size=20).")
    public PagedResponse<ReportSummaryResponse> getReports(
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return reportService.getForUser(userId, pageable);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get a single report, including its CSV content")
    public ReportResponse getReport(@PathVariable UUID reportId) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return reportService.getById(userId, reportId);
    }

    @GetMapping("/{reportId}/download")
    @Operation(summary = "Download a completed report's CSV content as an actual file",
            description = "Sets Content-Disposition so a browser/HTTP client saves it directly, instead of the "
                    + "caller having to write the JSON 'content' field to a file itself.")
    public ResponseEntity<String> downloadReport(@PathVariable UUID reportId) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        ReportResponse report = reportService.getById(userId, reportId);

        if (report.getStatus() != ReportStatus.COMPLETED || report.getContent() == null) {
            throw new BadRequestException("Report " + reportId + " has no downloadable content (status: "
                    + report.getStatus() + ")");
        }

        String filename = "report-" + report.getReportType() + "-" + report.getStartPeriod()
                + "-to-" + report.getEndPeriod() + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(report.getContent());
    }
}
