package com.platform.notification.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.platform.notification.entity.DeviceToken;
import com.platform.notification.repository.DeviceTokenRepository;
import com.platform.notification.service.FcmPushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends real FCM push notifications via the Firebase Admin SDK, to every
 * active device token registered for a user. One send per token (rather
 * than a multicast) so a single invalid token never affects delivery to
 * the user's other devices, and so an UNREGISTERED response can be tied
 * back to exactly the row that needs deactivating.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseFcmPushNotificationService implements FcmPushNotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    @Async("notificationTaskExecutor")
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<DeviceToken> devices = deviceTokenRepository.findAllByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active device tokens for user {} - skipping push", userId);
            return;
        }

        for (DeviceToken device : devices) {
            sendToDevice(device, title, body, data);
        }
    }

    private void sendToDevice(DeviceToken device, String title, String body, Map<String, String> data) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(device.getToken())
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());
            if (data != null) {
                messageBuilder.putAllData(data);
            }

            firebaseMessaging.send(messageBuilder.build());
        } catch (FirebaseMessagingException ex) {
            if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.info("Device token for user {} is no longer registered with FCM - deactivating", device.getUserId());
                device.setActive(false);
                deviceTokenRepository.save(device);
            } else {
                log.warn("Failed to send push notification to a device of user {}: {}",
                        device.getUserId(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Unexpected error sending push notification to a device of user {}: {}",
                    device.getUserId(), ex.getMessage());
        }
    }
}
