package com.hmg.ipmap.ipnotation.exception;

public class IpNotationUnknownIP extends RuntimeException {

    private static final String UNKNOWN_IP_MESSAGE = "Unknown IpAddress from ipNotation: ";

    public IpNotationUnknownIP(String part) {
        super(UNKNOWN_IP_MESSAGE + part);
    }

    public IpNotationUnknownIP(String message, Throwable cause) {
        super(message, cause);
    }
}
