package com.platform.notification.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final List<FieldValidationError> validationErrors;

    @Getter
    @Builder
    public static class FieldValidationError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}
