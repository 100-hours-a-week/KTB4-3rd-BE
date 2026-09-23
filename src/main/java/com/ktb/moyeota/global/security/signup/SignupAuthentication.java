package com.ktb.moyeota.global.security.signup;

import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.global.security.Authority;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class SignupAuthentication extends AbstractAuthenticationToken {

    private final SignupSessionView session;

    public SignupAuthentication(SignupSessionView session) {
        super(List.of(Authority.SIGNUP));
        this.session = session;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public SignupSessionView getPrincipal() {
        return session;
    }
}
