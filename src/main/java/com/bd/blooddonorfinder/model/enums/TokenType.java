package com.bd.blooddonorfinder.model.enums;

public enum TokenType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token");
    private final String value;
    TokenType(String value){
        this.value = value;
    }

    public String  getValue(){
        return  value;
    }

    /**
     * Resolves a token-type claim value ("access_token") to its enum constant.
     * JWT claims carry the lowercase value, not the enum constant name, so
     * {@code valueOf} cannot be used here.
     */
    public static TokenType fromValue(String value) {
        for (TokenType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported token type: " + value);
    }
}
