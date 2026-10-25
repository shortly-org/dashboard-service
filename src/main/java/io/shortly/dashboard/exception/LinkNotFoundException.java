package io.shortly.dashboard.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("link not found: " + code);
    }
}
