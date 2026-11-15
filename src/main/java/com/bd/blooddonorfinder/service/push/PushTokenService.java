package com.bd.blooddonorfinder.service.push;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.PushPlatform;
import com.bd.blooddonorfinder.model.push.PushToken;
import com.bd.blooddonorfinder.repository.PushTokenRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;

    public PushTokenService(PushTokenRepository pushTokenRepository, UserRepository userRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.userRepository = userRepository;
    }

    /** Idempotent: re-registering the same token updates its platform. */
    @Transactional
    public void register(UUID userId, String token, PushPlatform platform) {
        PushToken existing = pushTokenRepository.findByToken(token).orElse(null);
        if (existing != null) {
            if (!existing.getUser().getId().equals(userId)) {
                // a token is owned by exactly one account — re-registering from
                // another user (device reassignment) transfers ownership
                log.info("Push token reassigned from user={} to user={}",
                        existing.getUser().getId(), userId);
                existing.setUser(userRepository.getReferenceById(userId));
            }
            existing.setPlatform(platform);
            pushTokenRepository.save(existing);
            return;
        }
        PushToken pushToken = new PushToken();
        pushToken.setUser(userRepository.getReferenceById(userId));
        pushToken.setToken(token);
        pushToken.setPlatform(platform);
        pushTokenRepository.save(pushToken);
        log.info("Push token registered for user={} platform={}", userId, platform);
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        // scoped to the caller — a user can only ever remove their own tokens
        pushTokenRepository.deleteByUserIdAndToken(userId, token);
    }
}
