package com.bd.blooddonorfinder.exception;

public class DocumentMissingException extends RuntimeException{
    public DocumentMissingException(String msg){
        super(msg);
    }
}
