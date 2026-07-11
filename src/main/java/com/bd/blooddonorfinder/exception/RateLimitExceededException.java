package com.bd.blooddonorfinder.exception;

public class RateLimitExceededException extends RuntimeException{
    public RateLimitExceededException(String userId){
        super("Rate limit exceeded for user: " + userId);
    }
}
