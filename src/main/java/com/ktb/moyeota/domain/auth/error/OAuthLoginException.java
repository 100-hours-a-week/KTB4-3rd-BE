package com.ktb.moyeota.domain.auth.error;

import lombok.Getter;

@Getter
public class OAuthLoginException extends RuntimeException {

    private final OAuthLoginError error;

    public OAuthLoginException(OAuthLoginError error) {
        super(error.name());
        this.error = error;
    }

    public OAuthLoginException(OAuthLoginError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }
}
