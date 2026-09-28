package com.ktb.moyeota.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class OffsetAwareLocalDateTimeDeserializerTest {

    private static final LocalDateTime KST_18_21 = LocalDateTime.of(2026, 9, 28, 18, 21);

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addDeserializer(LocalDateTime.class,
                    new OffsetAwareLocalDateTimeDeserializer(ZoneId.of("Asia/Seoul"))))
            .build();

    record Body(LocalDateTime at) {
    }

    @ParameterizedTest(name = "{0}", quoteTextArguments = false)
    @ValueSource(strings = {
            "2026-09-28T09:21:00.000Z",
            "2026-09-28T09:21:00Z",
            "2026-09-28T18:21:00+09:00",
            "2026-09-28T10:21:00+01:00",
            "2026-09-28T18:21:00+09:00[Asia/Seoul]"
    })
    @DisplayName("오프셋이 있으면 같은 순간의 서버 시간대 시각으로 바꾼다")
    void convertsOffsetToServerZone(String value) {
        assertThat(read("\"" + value + "\"")).isEqualTo(KST_18_21);
    }

    @ParameterizedTest(name = "{0}", quoteTextArguments = false)
    @ValueSource(strings = {"2026-09-28T18:21:00", "2026-09-28T18:21", "2026-09-28T18:21:00.000"})
    @DisplayName("오프셋이 없으면 서버 시간대 시각으로 그대로 읽는다")
    void keepsLocalValue(String value) {
        assertThat(read("\"" + value + "\"")).isEqualTo(KST_18_21);
    }

    @Test
    @DisplayName("빈 값과 null은 null이다")
    void blankIsNull() {
        assertThat(read("\"\"")).isNull();
        assertThat(read("null")).isNull();
    }

    @Test
    @DisplayName("배열 형식도 지금처럼 읽는다")
    void readsArray() {
        assertThat(read("[2026,9,28,18,21]")).isEqualTo(KST_18_21);
    }

    @Test
    @DisplayName("시각이 아닌 문자열은 형식 오류다")
    void rejectsGarbage() {
        assertThatThrownBy(() -> read("\"abc\"")).isInstanceOf(DatabindException.class);
    }

    private LocalDateTime read(String json) {
        return mapper.readValue("{\"at\":" + json + "}", Body.class).at();
    }
}
