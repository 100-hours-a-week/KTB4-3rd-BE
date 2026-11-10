package com.ktb.moyeota.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdHandshakeInterceptorTest {

    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";

    private final TraceIdHandshakeInterceptor interceptor = new TraceIdHandshakeInterceptor();

    @Test
    @DisplayName("업그레이드 요청의 ALB 추적 헤더를 웹소켓 세션 속성에 남긴다")
    void keepsTraceIdInSessionAttributes() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(TraceId.HEADER, TRACE_ID);
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes).containsEntry(TraceId.LOG_KEY, TRACE_ID);
    }

    @Test
    @DisplayName("추적 헤더가 없어도 연결은 그대로 진행한다")
    void proceedsWithoutHeader() {
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes).doesNotContainKey(TraceId.LOG_KEY);
    }
}
