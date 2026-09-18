package com.ktb.moyeota.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, AccessTokenProvider.class,
        SecurityConfigTest.ProbeController.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("Boot 기본 체인이 아니라 우리 체인이 적용되어 토큰 없이도 요청이 통과한다")
    void permitsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/probe/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("유효한 액세스 토큰은 검증되어 sub가 인증 주체로 올라온다")
    void authenticatesValidToken() throws Exception {
        String token = accessTokenProvider.issue(42L).value();

        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    @DisplayName("위조된 토큰은 401로 거부된다")
    void rejectsForgedToken() throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF가 꺼져 있어 토큰 없는 POST도 403이 아니다")
    void csrfIsDisabled() throws Exception {
        mockMvc.perform(post("/probe/write"))
                .andExpect(status().isOk());
    }



    @RestController
    static class ProbeController {

        @GetMapping("/probe/me")
        String me(@AuthenticationPrincipal Jwt jwt) {
            return jwt == null ? "anonymous" : jwt.getSubject();
        }

        @PostMapping("/probe/write")
        String write() {
            return "ok";
        }
    }
}
