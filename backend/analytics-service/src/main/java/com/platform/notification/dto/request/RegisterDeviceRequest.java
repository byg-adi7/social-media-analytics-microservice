package com.platform.notification.dto.request;

import com.platform.notification.constant.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request payload for registering a device's FCM push token. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterDeviceRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotNull(message = "platform is required")
    private DevicePlatform platform;
}
