package com.premisave.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * A failure with a specific HTTP status, so callers get 404, 403, 409 and so on
 * instead of the blanket 400 that plain RuntimeExceptions produce.
 */
@SuppressWarnings("serial")
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Object details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }

    public Object getDetails() { return details; }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException unprocessable(String message, Object details) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, message, details);
    }
}