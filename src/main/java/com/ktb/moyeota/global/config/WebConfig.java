package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.security.resolver.AuthUserArgumentResolver;
import com.ktb.moyeota.global.security.resolver.SignupPrincipalArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthUserArgumentResolver authUserArgumentResolver;
    private final SignupPrincipalArgumentResolver signupPrincipalArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
        resolvers.add(signupPrincipalArgumentResolver);
    }
}
