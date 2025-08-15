package com.company.user.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String msg) { super(msg); }
}
