package com.example.howscat;

/**
 * 인증/인가 실패를 표현하기 위한 예외.
 */
public class AuthException extends RuntimeException {

    public AuthException() {
        super();
    }

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthException(Throwable cause) {
        super(cause);
    }
}

