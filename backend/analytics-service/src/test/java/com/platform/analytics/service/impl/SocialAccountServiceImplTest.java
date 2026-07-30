package com.platform.analytics.service.impl;

import com.platform.analytics.constant.AccountConnectionType;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.response.CsvImportResponse;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.validator.PlatformValidator;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceImplTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private SocialAccountMapper socialAccountMapper;

    @Mock
    private PlatformValidator platformValidator;

    @Mock
    private AnalyticsSyncService analyticsSyncService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SocialAccountServiceImpl socialAccountService;

    private UUID userId;
    private UUID accountId;
    private SocialAccount account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        account = SocialAccount.builder()
                .id(accountId)
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();
    }

    @Test
    void connectAccount_savesNewAccount_andTriggersInitialSync() {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token")
                .build();

        when(socialAccountRepository.existsByPlatformAndAccountId(Platform.YOUTUBE, "yt-123"))
                .thenReturn(false);
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(account);
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        SocialAccountResponse response = socialAccountService.connectAccount(userId, request);

        assertThat(response.getId()).isEqualTo(accountId);
        verify(platformValidator).validate(Platform.YOUTUBE);
        verify(analyticsSyncService).syncAccount(account);
        verify(notificationService).create(eq(userId), any(), any());
    }

    @Test
    void connectAccount_notificationCreateFails_stillReturnsSuccessfully() {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token")
                .build();

        when(socialAccountRepository.existsByPlatformAndAccountId(Platform.YOUTUBE, "yt-123"))
                .thenReturn(false);
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(account);
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());
        doThrow(new RuntimeException("notification create failed"))
                .when(notificationService).create(any(), any(), any());

        // A notification-creation failure must not fail an otherwise-successful connect.
        SocialAccountResponse response = socialAccountService.connectAccount(userId, request);

        assertThat(response.getId()).isEqualTo(accountId);
    }

    @Test
    void connectAccount_throwsBadRequest_whenAlreadyConnected() {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token")
                .build();

        when(socialAccountRepository.existsByPlatformAndAccountId(Platform.YOUTUBE, "yt-123"))
                .thenReturn(true);

        assertThatThrownBy(() -> socialAccountService.connectAccount(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already connected");

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void getAccountById_throwsNotFound_whenAccountDoesNotExist() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAccountService.getAccountById(userId, accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAccounts_returnsPagedResponse_withCorrectMetadata() {
        Pageable pageable = PageRequest.of(0, 20);
        when(socialAccountRepository.findAllByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(account), pageable, 1));
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        PagedResponse<SocialAccountResponse> result = socialAccountService.getAccounts(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(accountId);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    void disconnectAccount_deletesAccount_whenFound() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        socialAccountService.disconnectAccount(userId, accountId);

        // Analytics rows must be cleared first - the account row has a NOT
        // NULL FK from analytics, so deleting it first would fail. See the
        // regression this test guards against: disconnect used to throw
        // a foreign key violation for any account with generated analytics.
        verify(analyticsRepository).deleteBySocialAccountId(accountId);
        verify(socialAccountRepository).delete(account);
    }

    @Test
    void syncAccount_delegatesToAnalyticsSyncService() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        SocialAccountResponse response = socialAccountService.syncAccount(userId, accountId);

        assertThat(response.getId()).isEqualTo(accountId);
        verify(analyticsSyncService).syncAccount(account);
    }

    @Test
    void syncAccount_rejectsCsvImportAccount() {
        SocialAccount csvAccount = SocialAccount.builder()
                .id(accountId).userId(userId).platform(Platform.TWITTER)
                .accountId("csv-abc").connectionType(AccountConnectionType.CSV_IMPORT)
                .connectedAt(LocalDateTime.now()).active(true).build();
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(csvAccount));

        assertThatThrownBy(() -> socialAccountService.syncAccount(userId, accountId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSV");

        verify(analyticsSyncService, never()).syncAccount(any());
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "data.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importCsv_createsCsvImportAccount_andInsertsAllRows() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,1000,500,20,3,1
                2026-07-02,1050,520,22,4,2
                """;

        when(socialAccountRepository.save(any(SocialAccount.class))).thenAnswer(inv -> {
            SocialAccount a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(accountId);
            }
            return a;
        });
        when(analyticsRepository.findBySocialAccountIdAndAnalyticsDate(eq(accountId), any()))
                .thenReturn(Optional.empty());
        when(socialAccountMapper.toResponse(any())).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        CsvImportResponse response = socialAccountService.importCsv(userId, Platform.TWITTER, "My Twitter Export", csvFile(csv));

        assertThat(response.getRowsInserted()).isEqualTo(2);
        assertThat(response.getRowsUpdated()).isZero();
        assertThat(response.getAccount().getId()).isEqualTo(accountId);
        verify(platformValidator).validate(Platform.TWITTER);
        verify(analyticsRepository, times(2)).save(any(Analytics.class));
        // CSV import is not a live sync - must never trigger the sync service.
        verify(analyticsSyncService, never()).syncAccount(any());
        verify(notificationService).create(eq(userId), any(), any());

        var accountCaptor = org.mockito.ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository, atLeastOnce()).save(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues().get(0).getConnectionType()).isEqualTo(AccountConnectionType.CSV_IMPORT);
    }

    @Test
    void mergeCsv_upsertsByDate_intoExistingCsvImportAccount() {
        SocialAccount csvAccount = SocialAccount.builder()
                .id(accountId).userId(userId).platform(Platform.TWITTER)
                .accountId("csv-abc").connectionType(AccountConnectionType.CSV_IMPORT)
                .connectedAt(LocalDateTime.now()).active(true).build();
        Analytics existingRow = Analytics.builder()
                .socialAccount(csvAccount).analyticsDate(LocalDate.of(2026, 7, 1)).followers(900).build();

        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,1000,500,20,3,1
                2026-07-02,1050,520,22,4,2
                """;

        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(csvAccount));
        when(analyticsRepository.findBySocialAccountIdAndAnalyticsDate(accountId, LocalDate.of(2026, 7, 1)))
                .thenReturn(Optional.of(existingRow));
        when(analyticsRepository.findBySocialAccountIdAndAnalyticsDate(accountId, LocalDate.of(2026, 7, 2)))
                .thenReturn(Optional.empty());
        when(socialAccountMapper.toResponse(csvAccount)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        CsvImportResponse response = socialAccountService.mergeCsv(userId, accountId, csvFile(csv));

        assertThat(response.getRowsUpdated()).isEqualTo(1);
        assertThat(response.getRowsInserted()).isEqualTo(1);
        // The overlapping date's existing row must be updated in place, not duplicated.
        assertThat(existingRow.getFollowers()).isEqualTo(1000);
    }

    @Test
    void mergeCsv_rejectsAccountThatIsNotCsvImport() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> socialAccountService.mergeCsv(userId, accountId, csvFile("date,followers,views,likes,comments,shares\n2026-07-01,1,1,1,1,1\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CSV-imported");
    }
}
