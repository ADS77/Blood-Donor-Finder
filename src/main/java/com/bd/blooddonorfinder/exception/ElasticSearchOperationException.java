package com.bd.blooddonorfinder.exception;

public class ElasticSearchOperationException extends RuntimeException{
    public ElasticSearchOperationException(String message, Throwable cause){
        super(message, cause);
    }
}
