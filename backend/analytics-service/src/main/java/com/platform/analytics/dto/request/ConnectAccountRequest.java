package com.platform.analytics.dto.request;

import com.platform.analytics.constant.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for connecting a new social media account.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectAccountRequest {

    @NotNull(message = "platform is required")
    private Platform platform;

    @NotBlank(message = "accountId is required")
    private String accountId;

    @NotBlank(message = "accountName is required")
    private String accountName;

    private String username;

    private String profileImage;

    @NotBlank(message = "accessToken is required")
    private String accessToken;

    private String refreshToken;
}
