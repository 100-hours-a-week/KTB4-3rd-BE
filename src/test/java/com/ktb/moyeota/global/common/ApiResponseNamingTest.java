package com.ktb.moyeota.global.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiResponseNamingTest.ProbeController.class)
@Import(ApiResponseNamingTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiResponseNamingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("응답 본문의 필드명은 snake_case로 직렬화된다")
    void serializesFieldNamesAsSnakeCase() throws Exception {
        mockMvc.perform(get("/probe/naming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("token-value"))
                .andExpect(jsonPath("$.data.expires_in").value(1800))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").doesNotExist());
    }

    @Test
    @DisplayName("공통 응답의 한 단어 필드는 그대로 유지된다")
    void keepsSingleWordFields() throws Exception {
        mockMvc.perform(get("/probe/naming"))
                .andExpect(jsonPath("$.message").value("발급되었습니다"))
                .andExpect(jsonPath("$.data").exists());
    }

    record ProbeResponse(String accessToken, long expiresIn) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/naming")
        ApiResponse<ProbeResponse> probe() {
            return ApiResponse.success("발급되었습니다", new ProbeResponse("token-value", 1800));
        }
    }
}
