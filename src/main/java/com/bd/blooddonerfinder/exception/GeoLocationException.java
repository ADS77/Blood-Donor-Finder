package com.bd.blooddonerfinder.exception;

public class GeoLocationException extends RuntimeException{
    public GeoLocationException(String message, Throwable...  cause){
        super(message, cause[0]);
    }
}
