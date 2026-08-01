package com.platform.analytics.service.impl;

import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.service.SupabaseAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
public class SupabaseAdminServiceImpl implements SupabaseAdminService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void deleteUser(UUID userId) {
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_SERVICE_ROLE_KEY is not configured - account deletion cannot proceed");
        }

        String url = supabaseUrl + "/auth/v1/admin/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.set("apikey", serviceRoleKey);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Deleted Supabase Auth user {} - the user-deleted webhook will clean up app data", userId);
        } catch (HttpClientErrorException.NotFound ex) {
            // Already gone (e.g. a retried request) - treat as success, not an error.
            log.warn("Supabase Auth user {} was already deleted", userId);
        } catch (Exception ex) {
            log.error("Failed to delete Supabase Auth user {}: {}", userId, ex.getMessage());
            throw new ExternalApiException("Could not delete your account right now. Please try again.", ex);
        }
    }
}
