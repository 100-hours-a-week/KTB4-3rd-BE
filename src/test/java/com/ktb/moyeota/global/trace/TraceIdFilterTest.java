package com.ktb.moyeota.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.ScopeType;
import io.sentry.Sentry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";

    private final TraceIdFilter filter = new TraceIdFilter();

    @BeforeEach
    void setUp() {
        Sentry.init(options -> options.setDsn("https://key@sentry.invalid/1"));
    }

    @AfterEach
    void tearDown() {
        Sentry.close();
        MDC.clear();
    }

    @Test
    @DisplayName("ALB 추적 헤더를 요청 처리 중 로그와 Sentry 태그에 넣고, 끝나면 로그에서 뺀다")
    void putsTraceIdWhileHandlingRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceId.HEADER, TRACE_ID);
        AtomicReference<String> traceIdInChain = new AtomicReference<>();
        Map<String, String> sentryTags = new HashMap<>();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                traceIdInChain.set(MDC.get(TraceId.LOG_KEY));
                Sentry.configureScope(ScopeType.ISOLATION, scope -> sentryTags.putAll(scope.getTags()));
            }
        });

        assertThat(traceIdInChain.get()).isEqualTo(TRACE_ID);
        assertThat(sentryTags).containsEntry(TraceId.SENTRY_TAG, TRACE_ID);
        assertThat(MDC.get(TraceId.LOG_KEY)).isNull();
    }

    @Test
    @DisplayName("추적 헤더가 없으면 로그에 넣지 않는다")
    void skipsWithoutHeader() throws Exception {
        AtomicReference<String> traceIdInChain = new AtomicReference<>("not-called");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                traceIdInChain.set(MDC.get(TraceId.LOG_KEY));
            }
        });

        assertThat(traceIdInChain.get()).isNull();
    }
}
