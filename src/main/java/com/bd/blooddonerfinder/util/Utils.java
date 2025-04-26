package com.bd.blooddonerfinder.util;

import java.util.regex.Pattern;

public class Utils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                    + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$");

    public static boolean isValidEmail(String email){
        if(email == null || email.isBlank()){
            return false;
        }
        return EMAIL_PATTERN
                .matcher(email)
                .matches();
    }

}
