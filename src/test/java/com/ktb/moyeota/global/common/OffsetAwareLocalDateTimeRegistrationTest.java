package com.ktb.moyeota.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class OffsetAwareLocalDateTimeRegistrationTest {

    @Autowired
    private JsonMapper mapper;

    record Body(LocalDateTime at) {
    }

    @Test
    @DisplayName("애플리케이션 JSON 설정에 등록돼 오프셋이 있는 시각을 서버 시간대로 바꿔 읽는다")
    void registeredInApplicationMapper() {
        LocalDateTime expected = OffsetDateTime.parse("2026-09-28T18:21:00+09:00")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        Body body = mapper.readValue("{\"at\":\"2026-09-28T18:21:00+09:00\"}", Body.class);

        assertThat(body.at()).isEqualTo(expected);
    }
}
