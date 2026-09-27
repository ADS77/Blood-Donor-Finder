package com.bd.blooddonorfinder.exception;

public class RateLimitExceededException extends RuntimeException{
    public RateLimitExceededException(String msg){
        super(msg);
    }
}
