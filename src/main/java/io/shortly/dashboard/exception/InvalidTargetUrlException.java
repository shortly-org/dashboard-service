package io.shortly.dashboard.exception;

public class InvalidTargetUrlException extends RuntimeException {

    public InvalidTargetUrlException(String detail) {
        super(detail);
    }

    public InvalidTargetUrlException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
