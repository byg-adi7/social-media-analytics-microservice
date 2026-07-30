package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportResponse {

    private SocialAccountResponse account;
    private int rowsInserted;
    private int rowsUpdated;
}
