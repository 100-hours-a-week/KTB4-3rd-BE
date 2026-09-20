package com.ktb.moyeota.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = {ValidationPathTest.TestController.class, ValidationPathTest.AopValidatedController.class})
@Import({GlobalExceptionHandler.class, ValidationPathTest.TestController.class,
        ValidationPathTest.AopValidatedController.class})
class ValidationPathTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("@Validated 파라미터 검증 실패도 422와 details로 응답한다")
    void handlerMethodValidation() throws Exception {
        mockMvc.perform(get("/vp/param").param("nickname", "a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("@Validated가 붙은 컨트롤러의 파라미터 검증 실패도 422로 응답한다")
    void constraintViolation() throws Exception {
        mockMvc.perform(get("/vp/aop").param("nickname", "a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("클래스 레벨 제약은 필드를 지목하지 않으면 객체 이름이 field가 된다")
    void classLevelConstraintWithoutPropertyNode() throws Exception {
        mockMvc.perform(post("/vp/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bank_name\":\"국민\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("커스텀 제약이 필드를 지목하면 그 필드명이 field로 나간다")
    void classLevelConstraintWithPropertyNode() throws Exception {
        mockMvc.perform(post("/vp/targeted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bank_name\":\"국민\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].field").value("bank_name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Validated
    @RestController
    static class AopValidatedController {

        @GetMapping("/vp/aop")
        void aop(@RequestParam @Size(min = 2, max = 12) String nickname) {
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/vp/param")
        void param(@RequestParam @Size(min = 2, max = 12) String nickname) {
        }

        @PostMapping("/vp/plain")
        void plain(@Valid @RequestBody PlainRequest request) {
        }

        @PostMapping("/vp/targeted")
        void targeted(@Valid @RequestBody TargetedRequest request) {
        }
    }

    @PairRequired
    record PlainRequest(String bankName, String accountNo) {
    }

    @PairRequired(targetField = "bankName")
    record TargetedRequest(String bankName, String accountNo) {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PairRequiredValidator.class)
    @interface PairRequired {
        String message() default "REQUIRED";

        String targetField() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class PairRequiredValidator implements ConstraintValidator<PairRequired, Object> {

        private String targetField;

        @Override
        public void initialize(PairRequired annotation) {
            this.targetField = annotation.targetField();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            String bankName;
            String accountNo;
            if (value instanceof PlainRequest(String name, String no)) {
                bankName = name;
                accountNo = no;
            } else if (value instanceof TargetedRequest(String name, String no)) {
                bankName = name;
                accountNo = no;
            } else {
                return true;
            }

            if ((bankName == null) == (accountNo == null)) {
                return true;
            }
            if (!targetField.isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(targetField)
                        .addConstraintViolation();
            }
            return false;
        }
    }
}
