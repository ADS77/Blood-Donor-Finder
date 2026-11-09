package com.bd.blooddonorfinder.model.enums;

public enum OtpPurpose {
    LOGIN(true),
    REGISTER(true),
    PASSWORD_RESET(false),
    CHANGE_PHONE(false),
    CHANGE_EMAIL(false);
   private final boolean issueAuthToken;

    OtpPurpose(boolean issueAuthToken) {
        this.issueAuthToken = issueAuthToken;
    }

    public boolean isIssueAuthToken(){
        return issueAuthToken;
    }
}
