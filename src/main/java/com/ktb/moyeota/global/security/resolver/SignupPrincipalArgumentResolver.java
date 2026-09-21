package com.ktb.moyeota.global.security.resolver;

import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.global.security.signup.SignupAuthentication;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class SignupPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(SignupPrincipal.class)
                && SignupSessionView.class.equals(parameter.getParameterType());
    }

    @Override
    public SignupSessionView resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return CurrentAuthentication.require(SignupAuthentication.class).getPrincipal();
    }
}
