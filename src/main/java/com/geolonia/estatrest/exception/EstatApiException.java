package com.geolonia.estatrest.exception;

import org.springframework.http.HttpStatus;

/** A client- or server-facing API error with an explicit HTTP status. */
public class EstatApiException extends RuntimeException {

    private final HttpStatus status;

    public EstatApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
