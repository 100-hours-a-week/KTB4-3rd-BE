package com.ktb.moyeota.domain.congestion.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = CongestionMessagesJsonTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class CongestionMessagesJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("성공 결과 메시지를 단계 · 원인별 제보자 수 · 요약으로 읽는다")
    void readsSuccess() {
        String json = envelope("""
                {"spot_id":101,"window_end":"2026-10-13T18:00:00+09:00","analysis_status":"SUCCESS",
                 "result":{"congestion_level":"HIGH","report_count":2,
                           "causes":[{"cause_code":"EVENT","report_count":1}],
                           "summary":"제보 기준 혼잡도는 매우 혼잡이며, 혼잡 제보자는 2명입니다."},
                 "analyzed_at":"2026-10-13T18:00:41+09:00"}
                """);

        EventEnvelope<CongestionAnalysisResultData> envelope = read(json);

        assertThat(envelope.eventType()).isEqualTo(CongestionMessages.ANALYSIS_COMPLETED);
        assertThat(envelope.data()).isEqualTo(new CongestionAnalysisResultData(
                101L,
                OffsetDateTime.parse("2026-10-13T18:00:00+09:00"),
                AnalysisStatus.SUCCESS,
                new CongestionAnalysis(CongestionLevel.HIGH, 2,
                        List.of(new CongestionCause(CauseCode.EVENT, 1)),
                        "제보 기준 혼잡도는 매우 혼잡이며, 혼잡 제보자는 2명입니다."),
                OffsetDateTime.parse("2026-10-13T18:00:41+09:00")));
    }

    @Test
    @DisplayName("실패 결과 메시지는 result 없이 읽는다")
    void readsFailure() {
        String json = envelope("""
                {"spot_id":101,"window_end":"2026-10-13T18:00:00+09:00","analysis_status":"FAILED","result":null}
                """);

        CongestionAnalysisResultData data = read(json).data();

        assertThat(data.analysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(data.result()).isNull();
    }

    @Test
    @DisplayName("BE 가 모르는 원인 코드는 UNKNOWN 으로 읽는다")
    void readsUnknownCauseCodeAsUnknown() {
        String json = envelope("""
                {"spot_id":101,"window_end":"2026-10-13T18:00:00+09:00","analysis_status":"SUCCESS",
                 "result":{"congestion_level":"LOW","report_count":1,
                           "causes":[{"cause_code":"NEW_CAUSE","report_count":1}],"summary":"요약"},
                 "analyzed_at":"2026-10-13T18:00:41+09:00"}
                """);

        CongestionAnalysis result = read(json).data().result();

        assertThat(result.causes()).containsExactly(new CongestionCause(CauseCode.UNKNOWN, 1));
    }

    @Test
    @DisplayName("성공인데 result 가 없으면 잘못된 메시지다")
    void rejectsSuccessWithoutResult() {
        String json = envelope("""
                {"spot_id":101,"window_end":"2026-10-13T18:00:00+09:00","analysis_status":"SUCCESS","result":null}
                """);

        assertThatThrownBy(() -> read(json)).hasRootCauseInstanceOf(InvalidEventException.class);
    }

    @Test
    @DisplayName("spot_id 나 window_end 가 없으면 잘못된 메시지다")
    void rejectsMissingKeys() {
        String json = envelope("""
                {"spot_id":101,"analysis_status":"FAILED","result":null}
                """);

        assertThatThrownBy(() -> read(json)).hasRootCauseInstanceOf(InvalidEventException.class);
    }

    private EventEnvelope<CongestionAnalysisResultData> read(String json) {
        return jsonMapper.readValue(json, new TypeReference<>() {
        });
    }

    private static String envelope(String data) {
        return """
                {"event_id":"7d2a0c1e-3b7d-4f7e-9a51-6f2c8d1e4b90","event_type":"congestion.analysis.completed",
                 "schema_version":1,"occurred_at":"2026-10-13T18:00:41.000+09:00","data":%s}
                """.formatted(data);
    }
}
