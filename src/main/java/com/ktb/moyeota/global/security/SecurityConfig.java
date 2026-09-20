package com.ktb.moyeota.global.security;

import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.UserIdJwtAuthenticationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] OAUTH_LOGIN_PATHS = {"/auth/*/login", "/auth/*/callback"};

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, OAUTH_LOGIN_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/tokens").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/auth/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users")
                        .hasAuthority(Authority.SIGNUP_NAME)
                        .requestMatchers(HttpMethod.GET, "/users/nickname-availability")
                        .hasAnyAuthority(Authority.SIGNUP_NAME, Authority.USER_NAME)
                        .anyRequest().hasAuthority(Authority.USER_NAME))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new UserIdJwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

}
