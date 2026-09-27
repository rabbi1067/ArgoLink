package com.agrolink.app.exception;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {

    private final int status;

    public BusinessRuleException(String message) {
        this(message, 422);
    }

    public BusinessRuleException(String message, int status) {
        super(message);
        this.status = status;
    }
}