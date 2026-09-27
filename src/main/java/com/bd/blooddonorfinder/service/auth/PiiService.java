package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.common.User;

public interface PiiService {
    String decryptEmail(User user);

    String decryptPhone(User user);

    String encryptEmail(String email);

    String encryptPhone(String phone);
}
