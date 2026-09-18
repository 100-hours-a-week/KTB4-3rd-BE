package com.ktb.moyeota.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class Authority {

    public static final String USER_NAME = "USER";

    public static final GrantedAuthority USER = new SimpleGrantedAuthority(USER_NAME);

    private Authority() {
    }
}
