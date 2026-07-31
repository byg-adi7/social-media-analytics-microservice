package com.platform.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Full-replace request payload for a user's notification preferences. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationPreferenceRequest {

    @NotNull(message = "pushEnabled is required")
    private Boolean pushEnabled;

    @NotNull(message = "emailEnabled is required")
    private Boolean emailEnabled;
}
