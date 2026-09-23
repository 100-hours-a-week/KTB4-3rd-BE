package com.ktb.moyeota.domain.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserControllerRejectionTest extends SignupApiTestSupport {

    @Test
    @DisplayName("약관 객체를 보내지 않으면 422 REQUIRED다")
    void omittedAgreements() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE"}
                        """).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("agreements"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 그 항목을 가리켜 422다")
    void declinedRequiredAgreement() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","agreements":
                          {"service":true,"location":false,"gender":true,"account_third_party":false,"marketing":false}}
                        """).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.field").value("agreements.location"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("선택 약관 키를 빠뜨리면 snake_case 경로로 알려준다")
    void omittedOptionalAgreementKey() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","agreements":
                          {"service":true,"location":true,"gender":true,"marketing":false}}
                        """).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("agreements.account_third_party"));
    }

    @Test
    @DisplayName("허용되지 않는 성별은 422 INVALID_ENUM이다")
    void invalidGender() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"OTHER","agreements":%s}
                        """.formatted(ALL_AGREED)).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("gender"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));
    }

    @Test
    @DisplayName("여러 필드가 틀리면 중첩 필드까지 선언 순서대로 알려준다")
    void detailsFollowDeclarationOrder() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길","agreements":
                          {"service":true,"location":true,"gender":false,"account_third_party":false,"marketing":false}}
                        """).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[1].field").value("gender"))
                .andExpect(jsonPath("$.error.details[2].field").value("agreements.gender"));
    }

    @Test
    @DisplayName("은행명만 보내면 bank_name을 가리켜 422 REQUIRED다")
    void bankNameWithoutAccountNo() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","bank_name":"shinhan","agreements":%s}
                        """.formatted(ALL_AGREED)).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("지원하지 않는 은행과 형식이 틀린 계좌번호는 각각의 필드와 사유로 알려준다")
    void invalidBankAccount() throws Exception {
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","bank_name":"모여타은행","account_no":"12-34","agreements":%s}
                        """.formatted(ALL_AGREED)).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.error.details[1].field").value("account_no"))
                .andExpect(jsonPath("$.error.details[1].reason").value("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("프로필 이미지 키는 null로 보내도, 값을 보내도 받는다")
    void acceptsProfileImageKey() throws Exception {
        givenRegistered();

        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","profile_image_key":null,"agreements":%s}
                        """.formatted(ALL_AGREED)).cookie(validSignupCookie()))
                .andExpect(status().isCreated());
        mockMvc.perform(signUp("""
                        {"nickname":"길동이","gender":"FEMALE","profile_image_key":"tmp/profile/s-1a2b/0b9c.jpg","agreements":%s}
                        """.formatted(ALL_AGREED)).cookie(validSignupCookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile_image_url").value(nullValue()));
    }

    @Test
    @DisplayName("프로필 이미지가 업로드되지 않았으면 422 IMAGE_NOT_EXISTS다")
    void imageNotExists() throws Exception {
        given(userService.register(any(), any()))
                .willThrow(new BusinessException(ImageErrorCode.IMAGE_NOT_EXISTS));

        mockMvc.perform(signUp(VALID_BODY).cookie(validSignupCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("IMAGE_NOT_EXISTS"));
    }

    @Test
    @DisplayName("닉네임이 이미 쓰이고 있으면 409 NICKNAME_DUPLICATE다")
    void duplicateNickname() throws Exception {
        given(userService.register(any(), any()))
                .willThrow(new BusinessException(UserErrorCode.NICKNAME_DUPLICATE));

        mockMvc.perform(signUp(VALID_BODY).cookie(validSignupCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATE"))
                .andExpect(cookie().doesNotExist(REFRESH_TOKEN));
    }
}
