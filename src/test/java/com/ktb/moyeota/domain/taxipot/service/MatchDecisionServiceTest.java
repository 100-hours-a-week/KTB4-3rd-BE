package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRejectedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.AlreadyApplied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Rejected;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchDecisionServiceTest {

    private static final String CELL_KEY = "2079:5606";
    private static final MatchDecidedData DECIDED = new MatchDecidedData("11,12", "11,12",
            List.of(MatchRequestData.from(matchRequest(11L)), MatchRequestData.from(matchRequest(12L))),
            11L, 11L, DEPARTURE_AT);

    private TaxiPotMatchReflector reflector;
    private OutboxRecorder outboxRecorder;
    private MatchDecisionService matchDecisionService;

    @BeforeEach
    void setUp() {
        reflector = mock(TaxiPotMatchReflector.class);
        outboxRecorder = mock(OutboxRecorder.class);
        matchDecisionService = new MatchDecisionService(reflector, outboxRecorder);
    }

    @Test
    @DisplayName("결정을 매칭 결과로 바꿔 반영기에 넘기고, 반영되면 아웃박스에 아무것도 남기지 않는다")
    void appliesDecision() {
        given(reflector.apply(DECIDED.toMatchResult())).willReturn(new Applied(55L));

        MatchApplyOutcome outcome = matchDecisionService.apply(CELL_KEY, DECIDED);

        assertThat(outcome).isEqualTo(new Applied(55L));
        verify(reflector).apply(DECIDED.toMatchResult());
        verify(outboxRecorder, never()).record(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("이미 반영된 결정이면 아웃박스에 아무것도 남기지 않는다")
    void skipsAlreadyApplied() {
        given(reflector.apply(DECIDED.toMatchResult())).willReturn(new AlreadyApplied(55L));

        assertThat(matchDecisionService.apply(CELL_KEY, DECIDED)).isEqualTo(new AlreadyApplied(55L));
        verify(outboxRecorder, never()).record(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("반영이 거절되면 받은 칸 키로 matching.rejected 를 아웃박스에 남긴다")
    void recordsRejected() {
        given(reflector.apply(DECIDED.toMatchResult())).willReturn(new Rejected(RejectReason.POT_FULL));

        MatchApplyOutcome outcome = matchDecisionService.apply(CELL_KEY, DECIDED);

        assertThat(outcome).isEqualTo(new Rejected(RejectReason.POT_FULL));
        verify(outboxRecorder).record(MatchingMessages.REQUEST_TOPIC, CELL_KEY, MatchingMessages.REJECTED,
                MatchingMessages.SCHEMA_VERSION,
                new MatchRejectedData("11,12", "11,12", List.of(11L, 12L), RejectReason.POT_FULL));
    }

    @Test
    @DisplayName("반영 중 예외가 나면 그대로 던져 다시 시도하게 하고 아웃박스에 남기지 않는다")
    void propagatesFailure() {
        given(reflector.apply(DECIDED.toMatchResult())).willThrow(new IllegalStateException("DB 오류"));

        assertThatThrownBy(() -> matchDecisionService.apply(CELL_KEY, DECIDED))
                .isInstanceOf(IllegalStateException.class);
        verify(outboxRecorder, never()).record(anyString(), anyString(), anyString(), anyInt(), any());
    }
}
