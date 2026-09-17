package com.ktb.moyeota.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException은 ErrorCode에 정의된 상태와 코드로 변환된다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_FULL"))
                .andExpect(jsonPath("$.message").value("정원이 가득 찼습니다."))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 실패는 422와 필드별 사유를 담아 응답한다")
    void validationError() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\",\"termsAgreed\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(3));
    }

    @Test
    @DisplayName("응답의 field는 자바 필드명이 아니라 snake_case로 나간다")
    void fieldNameIsSnakeCase() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"valid\",\"termsAgreed\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("terms_agreed"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("길이 제약 위반은 최대 초과와 최소 미달 모두 LENGTH_OUT_OF_RANGE로 변환된다")
    void sizeViolationBecomesLengthOutOfRange() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"닉네임이아주아주아주길어요\",\"termsAgreed\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"a\",\"termsAgreed\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));
    }

    @RepeatedTest(5)
    @DisplayName("details는 DTO 필드 선언 순서로 정렬되어 매번 같은 순서로 나온다")
    void detailsAreSortedByDeclarationOrder() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\",\"termsAgreed\":false,\"bankName\":\"없는은행\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details.length()").value(4))
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.error.details[1].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[1].reason").value("LENGTH_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.error.details[2].field").value("terms_agreed"))
                .andExpect(jsonPath("$.error.details[3].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[3].reason").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.error.field").value("nickname"));
    }

    @Test
    @DisplayName("제약에 선언한 message가 애노테이션 기본 매핑보다 우선한다")
    void declaredMessageWinsOverAnnotationMapping() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"valid\",\"termsAgreed\":true,\"bankName\":\"없는은행\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));
    }

    @Test
    @DisplayName("읽을 수 없는 JSON은 400 MALFORMED_REQUEST로 응답한다")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 400과 해당 필드를 함께 알려준다")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.details[0].field").value("size"));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 422 REQUIRED로 응답한다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("허용되지 않은 HTTP 메서드는 405로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500으로 응답하고 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404 ENDPOINT_NOT_FOUND로 응답한다")
    void endpointNotFound() throws Exception {
        mockMvc.perform(get("/test/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(TestErrorCode.CAPACITY_FULL);
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/param")
        void param(@RequestParam int size) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("커넥션 풀 고갈: jdbc:mysql://internal-host:3306");
        }
    }

    record TestRequest(
            @NotBlank @Size(min = 2, max = 12) String nickname,
            @AssertTrue Boolean termsAgreed,
            @Pattern(regexp = "국민|신한|우리", message = "INVALID_ENUM") String bankName) {
    }

    @Getter
    @RequiredArgsConstructor
    enum TestErrorCode implements ErrorCode {

        CAPACITY_FULL(HttpStatus.CONFLICT, "정원이 가득 찼습니다.");

        private final HttpStatus status;
        private final String message;
    }
}
