package com.platform.analytics.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Initializes the Firebase Admin SDK for sending FCM push notifications.
 * Only active when firebase.enabled=true - see FirebaseFcmPushNotificationService
 * for the sender and NoopFcmPushNotificationService for the no-op fallback
 * used the rest of the time.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties firebaseProperties;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (InputStream credentialsStream = resolveCredentialsStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized for FCM push notifications");
            return app;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private InputStream resolveCredentialsStream() throws IOException {
        String base64 = firebaseProperties.getServiceAccountBase64();
        if (base64 != null && !base64.isBlank()) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(base64));
        }

        String path = firebaseProperties.getServiceAccountPath();
        if (path != null && !path.isBlank()) {
            return new FileInputStream(path);
        }

        throw new IllegalStateException(
                "firebase.enabled=true but neither FIREBASE_SERVICE_ACCOUNT_BASE64 nor "
                        + "FIREBASE_SERVICE_ACCOUNT_PATH is set");
    }
}
