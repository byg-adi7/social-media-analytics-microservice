package com.platform.notification.dto.request;

import com.platform.notification.constant.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotNull
    private ReportType reportType;

    @NotNull
    private LocalDate startPeriod;

    @NotNull
    private LocalDate endPeriod;
}
