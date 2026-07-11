package com.bd.blooddonorfinder.exception;

public class UnknownEventTypeException extends RuntimeException{
    public UnknownEventTypeException(String eventType){
        super("No handler registered for event type: "+ eventType);
    }
}
