package com.ktb.moyeota.global.security.resolver;

import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class CurrentAuthentication {

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private CurrentAuthentication() {
    }

    static <T extends Authentication> T require(Class<T> type) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || TRUST_RESOLVER.isAnonymous(authentication)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!type.isInstance(authentication)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return type.cast(authentication);
    }
}
