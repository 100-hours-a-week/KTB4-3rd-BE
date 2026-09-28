package com.ktb.moyeota.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class Authority {

    public static final String USER_NAME = "USER";

    public static final String SIGNUP_NAME = "SIGNUP";

    public static final GrantedAuthority USER = new SimpleGrantedAuthority(USER_NAME);

    public static final GrantedAuthority SIGNUP = new SimpleGrantedAuthority(SIGNUP_NAME);

    private Authority() {
    }
}
