package com.agrolink.app.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class EntityConflictException extends RuntimeException {

    private final HttpStatus status;

    public EntityConflictException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public EntityConflictException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}