package com.ktb.moyeota.global.security.resolver;

import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.signup.SignupAuthentication;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class UploadScopeArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UploadPrincipal.class)
                && UploadScope.class.equals(parameter.getParameterType());
    }

    @Override
    public UploadScope resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = CurrentAuthentication.require(Authentication.class);
        return switch (authentication) {
            case SignupAuthentication signup -> UploadScope.signup(signup.getPrincipal().tokenHash());
            case JwtAuthenticationToken jwt -> UploadScope.user(Long.valueOf(jwt.getToken().getSubject()));
            default -> throw new BusinessException(CommonErrorCode.FORBIDDEN);
        };
    }
}
