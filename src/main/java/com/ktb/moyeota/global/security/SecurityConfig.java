package com.ktb.moyeota.global.security;

import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.UserIdJwtAuthenticationConverter;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] OAUTH_LOGIN_PATHS = {"/api/auth/*/login", "/api/auth/*/callback"};

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            SignupSessionAuthenticator signupSessionAuthenticator) throws Exception {
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
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, OAUTH_LOGIN_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/tokens").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users")
                        .hasAuthority(Authority.SIGNUP_NAME)
                        .requestMatchers(HttpMethod.GET, "/api/users/nickname-availability")
                        .hasAnyAuthority(Authority.SIGNUP_NAME, Authority.USER_NAME)
                        .requestMatchers(HttpMethod.POST, "/api/images/presigned-url")
                        .hasAnyAuthority(Authority.SIGNUP_NAME, Authority.USER_NAME)
                        .requestMatchers(HttpMethod.GET, "/api/map-pins", "/api/nearby-posts").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().hasAuthority(Authority.USER_NAME))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new UserIdJwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        signupSessionAuthenticationFilter(signupSessionAuthenticator),
                        AuthorizationFilter.class);
        return http.build();
    }

    private AuthenticationFilter signupSessionAuthenticationFilter(
            SignupSessionAuthenticator signupSessionAuthenticator) {

        AuthenticationManager alreadyVerified = authentication -> authentication;
        AuthenticationFilter filter = new AuthenticationFilter(
                alreadyVerified, signupSessionAuthenticator::authenticate);
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        filter.setRequestMatcher(new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/users"),
                paths.matcher(HttpMethod.GET, "/api/users/nickname-availability"),
                paths.matcher(HttpMethod.POST, "/api/images/presigned-url")));
        filter.setSuccessHandler((request, response, authentication) -> {
        });
        return filter;
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
