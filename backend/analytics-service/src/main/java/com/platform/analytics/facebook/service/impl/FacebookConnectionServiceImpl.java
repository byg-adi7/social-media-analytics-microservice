package com.platform.analytics.facebook.service.impl;

import com.platform.analytics.constant.AccountConnectionType;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ConflictException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.facebook.FacebookProperties;
import com.platform.analytics.facebook.api.FacebookApiClient;
import com.platform.analytics.facebook.api.dto.FacebookAccountsResponse;
import com.platform.analytics.facebook.api.dto.FacebookPageResponse;
import com.platform.analytics.facebook.api.dto.FacebookTokenResponse;
import com.platform.analytics.facebook.service.FacebookConnectionService;
import com.platform.analytics.facebook.service.FacebookOAuthService;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookConnectionServiceImpl implements FacebookConnectionService {

    private static final String PAGE_FIELDS = "id,name,category,followers_count,fan_count,picture{url}";

    private final FacebookOAuthService facebookOAuthService;
    private final FacebookApiClient facebookApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final FacebookProperties facebookProperties;

    @Override
    public String getAuthorizationUrl(UUID userId) {
        assertIntegrationEnabled();
        return facebookOAuthService.buildAuthorizationUrl(userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse completeConnection(String code, String state) {
        assertIntegrationEnabled();
        UUID userId = stateTokenService.verifyAndExtractUserId(state);

        log.info("Completing Facebook OAuth connection for user {}", userId);

        FacebookTokenResponse shortLived = facebookOAuthService.exchangeCodeForToken(code);
        FacebookTokenResponse longLived = facebookOAuthService.exchangeForLongLivedToken(shortLived.accessToken());

        FacebookAccountsResponse.Page page = fetchFirstManagedPage(longLived.accessToken());
        FacebookPageResponse pageDetails = fetchPageDetails(page.id(), page.accessToken());

        String profileImage = pageDetails.picture() != null && pageDetails.picture().data() != null
                ? pageDetails.picture().data().url()
                : null;

        SocialAccount account = resolveAccountForConnection(userId, page.id());

        account.setAccountName(pageDetails.name() != null ? pageDetails.name() : page.name());
        account.setProfileImage(profileImage);
        account.setAccessToken(page.accessToken());

        // Page access tokens derived from a long-lived user token do not
        // expire — there is no refresh token or expiry to track here,
        // unlike every other platform in this service. See
        // FacebookSocialMediaClient for what happens if it's ever revoked.
        account.setRefreshToken(null);
        account.setTokenExpiresAt(null);
        account.setConnectionType(AccountConnectionType.OAUTH);
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected/updated real Facebook Page {} (pageId={}) for user {}", saved.getId(), page.id(), userId);

        analyticsSyncService.syncAccount(saved);

        return socialAccountMapper.toResponse(saved);
    }

    private SocialAccount resolveAccountForConnection(UUID userId, String pageId) {
        Optional<SocialAccount> existing =
                socialAccountRepository.findByPlatformAndAccountId(Platform.FACEBOOK, pageId);

        if (existing.isEmpty()) {
            return SocialAccount.builder()
                    .userId(userId)
                    .platform(Platform.FACEBOOK)
                    .accountId(pageId)
                    .connectedAt(LocalDateTime.now())
                    .build();
        }

        SocialAccount existingAccount = existing.get();
        if (existingAccount.getUserId().equals(userId)) {
            if (!existingAccount.isActive()) {
                analyticsRepository.deleteBySocialAccountId(existingAccount.getId());
                existingAccount.setConnectedAt(LocalDateTime.now());
            }
            return existingAccount;
        }

        if (!existingAccount.isActive()) {
            analyticsRepository.deleteBySocialAccountId(existingAccount.getId());
            socialAccountRepository.delete(existingAccount);
            socialAccountRepository.flush();
            return SocialAccount.builder()
                    .userId(userId)
                    .platform(Platform.FACEBOOK)
                    .accountId(pageId)
                    .connectedAt(LocalDateTime.now())
                    .build();
        }

        throw new ConflictException("This Facebook Page is already connected to another user");
    }

    private FacebookAccountsResponse.Page fetchFirstManagedPage(String longLivedUserToken) {
        try {
            FacebookAccountsResponse response = facebookApiClient.getManagedPages("me", longLivedUserToken);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new ExternalApiException(
                        "Facebook returned no managed Pages for this account — the connecting person must be an " +
                                "admin or editor of at least one Facebook Page");
            }
            return response.data().get(0);
        } catch (FeignException ex) {
            log.error("Failed to fetch managed Pages during Facebook connect: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to fetch managed Facebook Pages", ex);
        }
    }

    private FacebookPageResponse fetchPageDetails(String pageId, String pageAccessToken) {
        try {
            return facebookApiClient.getPage(pageId, PAGE_FIELDS, pageAccessToken);
        } catch (FeignException ex) {
            log.error("Failed to fetch Page details during Facebook connect: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to fetch Facebook Page details", ex);
        }
    }

    private void assertIntegrationEnabled() {
        if (!facebookProperties.isEnabled()) {
            throw new BadRequestException(
                    "The real Facebook integration is not enabled on this server (facebook.enabled=false). " +
                            "Connected accounts currently use simulated data via MockSocialMediaClient.");
        }
    }
}
