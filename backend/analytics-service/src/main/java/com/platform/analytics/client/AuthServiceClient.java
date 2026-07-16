package com.platform.analytics.client;

import com.platform.analytics.dto.response.TokenValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client used to delegate JWT validation to the central Auth Service.
 * The Analytics Service performs NO local authentication — every incoming
 * request's token is verified remotely via this client.
 */
@FeignClient(name = "auth-service", url = "${auth-service.url}")
public interface AuthServiceClient {

    @GetMapping("${auth-service.validate-token-path}")
    TokenValidationResponse validateToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken);
}
