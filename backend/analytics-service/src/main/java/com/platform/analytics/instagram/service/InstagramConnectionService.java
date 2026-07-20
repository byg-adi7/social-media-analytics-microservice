package com.platform.analytics.instagram.service;

import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.UUID;

public interface InstagramConnectionService {

    String getAuthorizationUrl(UUID userId);

    SocialAccountResponse completeConnection(String code, String state);
}
