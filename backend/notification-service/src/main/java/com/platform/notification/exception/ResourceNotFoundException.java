package com.platform.notification.exception;

import com.platform.notification.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND);
    }

    public static ResourceNotFoundException forEntity(String entityName, Object identifier) {
        return new ResourceNotFoundException(entityName + " not found with id: " + identifier);
    }
}
