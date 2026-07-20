package com.platform.notification.dto.request;

import com.platform.notification.constant.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Body for the internal-only POST /internal/notifications, called by other
 * backend services (currently: Analytics Service) on real events - not
 * exposed to end users, who never author their own notifications.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private NotificationType type;

    @NotBlank
    private String message;
}
