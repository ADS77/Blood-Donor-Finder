package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.payload.enums.LoginIdentifierType;

public class LoginIdentifierResolver {
    public static LoginIdentifierType resolve(String identifier) {
        if (identifier.contains("@")) {
            return LoginIdentifierType.EMAIL;
        }
        return LoginIdentifierType.PHONE;
    }
}
