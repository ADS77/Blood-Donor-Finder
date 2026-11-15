package com.bd.blooddonorfinder.service.push;

import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.push.PushToken;
import com.bd.blooddonorfinder.repository.PushTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FCM push sender. Design rules:
 * <ul>
 *   <li><b>Never blocks the business thread</b> — sends run on a small
 *       dedicated executor with a bounded queue (overflow drops oldest push,
 *       never the transaction).</li>
 *   <li><b>Never breaks the flow</b> — every failure is caught and logged;
 *       pushes are best-effort by contract.</li>
 *   <li><b>Self-healing tokens</b> — unregistered/invalid FCM tokens are
 *       deleted, so stale devices never accumulate.</li>
 *   <li><b>Graceful degradation</b> — with {@code app.push.enabled=false} or a
 *       missing credentials file the app runs normally, just without pushes.</li>
 * </ul>
 */
@Service
@Slf4j
public class FcmPushSender implements PushSender {

    private static final int MAX_QUEUE = 2_000;

    private final PushTokenRepository pushTokenRepository;
    private final boolean enabled;
    private final String credentialsPath;
    private volatile FirebaseMessaging messaging;
    private final ExecutorService executor;

    public FcmPushSender(PushTokenRepository pushTokenRepository,
                         @Value("${app.push.enabled:false}") boolean enabled,
                         @Value("${app.push.credentials-path:}") String credentialsPath) {
        this.pushTokenRepository = pushTokenRepository;
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
        // daemon threads: pushes must never block JVM shutdown
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "fcm-push-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        this.executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUE),
                factory,
                // bounded queue: pushes are best-effort, dropping the oldest under
                // burst pressure is better than OOM or blocking the caller
                new ThreadPoolExecutor.DiscardOldestPolicy());
        if (enabled) {
            tryInit();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && messaging != null;
    }

    private synchronized void tryInit() {
        if (messaging != null || credentialsPath == null || credentialsPath.isBlank()) {
            return;
        }
        try (InputStream in = Files.newInputStream(Path.of(credentialsPath))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(in))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            this.messaging = FirebaseMessaging.getInstance();
            log.info("FCM push initialized from {}", credentialsPath);
        } catch (Exception e) {
            // push stays disabled — log once, keep the app fully functional
            log.error("FCM initialization failed (push disabled): {}", e.getMessage(), e);
        }
    }

    @Override
    public void send(UUID userId, NotificationType type, UUID referenceId, Map<String, Object> payload) {
        if (!isEnabled()) {
            return;
        }
        List<PushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            return;
        }
        String title = PushMessages.title(type, payload);
        String body = PushMessages.body(type, payload);
        Map<String, String> data = flatten(type, referenceId, payload);

        for (PushToken token : tokens) {
            String deviceToken = token.getToken();
            try {
                executor.execute(() -> deliver(deviceToken, title, body, data, type));
            } catch (Exception e) {
                // e.g. executor shutting down — never let a push failure reach
                // the business thread
                log.debug("Push dispatch skipped for user={}: {}", userId, e.getMessage());
            }
        }
    }

    private void deliver(String deviceToken, String title, String body,
                         Map<String, String> data, NotificationType type) {
        try {
            Message.Builder builder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data);
            builder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setChannelId("urgent-alerts")
                            .build())
                    .build());
            builder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder().setSound("default").setBadge(1).build())
                    .build());
            messaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("Removing dead FCM token ({}): {}", code, mask(deviceToken));
                pushTokenRepository.deleteByToken(deviceToken);
            } else {
                log.warn("FCM send failed for type={}: {}", type, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected push failure for type={}: {}", type, e.getMessage(), e);
        }
    }

    /** Data payload for deep-linking; everything as strings (FCM requirement). */
    private static Map<String, String> flatten(NotificationType type, UUID referenceId,
                                               Map<String, Object> payload) {
        Map<String, String> data = new java.util.HashMap<>();
        data.put("type", type.name());
        if (referenceId != null) {
            data.put("referenceId", referenceId.toString());
        }
        if (payload != null) {
            payload.forEach((k, v) -> {
                if (v != null) {
                    data.put(k, String.valueOf(v));
                }
            });
        }
        return data;
    }

    private static String mask(String token) {
        return token.length() <= 12 ? "***" : token.substring(0, 8) + "...";
    }
}
