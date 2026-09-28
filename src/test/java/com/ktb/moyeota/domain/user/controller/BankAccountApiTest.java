package com.ktb.moyeota.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.MaskedBankAccount;
import com.ktb.moyeota.global.security.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class BankAccountApiTest extends SignupApiTestSupport {

    @Test
    @DisplayName("계좌를 저장하고 은행 코드와 마스킹한 계좌번호를 내린다")
    void replace() throws Exception {
        given(userService.replaceBankAccount(42L, new BankAccountCommand("shinhan", "11012345678")))
                .willReturn(new MaskedBankAccount("shinhan", "*******5678"));

        mockMvc.perform(replaceBankAccount("""
                        {"bank_name":"shinhan","account_no":"110-123-45678"}""").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("정산 계좌가 저장되었습니다"))
                .andExpect(jsonPath("$.data.bank_name").value("shinhan"))
                .andExpect(jsonPath("$.data.account_no_masked").value("*******5678"));
    }

    @Test
    @DisplayName("계좌번호가 빠지면 422 account_no REQUIRED이고 저장하지 않는다")
    void missingAccountNo() throws Exception {
        mockMvc.perform(replaceBankAccount("""
                        {"bank_name":"shinhan"}""").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("account_no"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("지원하지 않는 은행이면 422 bank_name INVALID_ENUM이다")
    void unsupportedBank() throws Exception {
        mockMvc.perform(replaceBankAccount("""
                        {"bank_name":"KB국민은행","account_no":"11012345678"}""").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("계좌번호 자릿수가 틀리면 422 account_no다")
    void invalidAccountNo() throws Exception {
        mockMvc.perform(replaceBankAccount("""
                        {"bank_name":"shinhan","account_no":"123456789"}""").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("account_no"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(replaceBankAccount("""
                        {"bank_name":"shinhan","account_no":"11012345678"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(userService);
    }

    private static MockHttpServletRequestBuilder replaceBankAccount(String body) {
        return put("/api/users/me/bank-account").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
