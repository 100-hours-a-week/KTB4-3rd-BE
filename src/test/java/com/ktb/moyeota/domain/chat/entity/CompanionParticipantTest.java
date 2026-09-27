package com.ktb.moyeota.domain.chat.entity;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompanionParticipantTest {

    private final User member = user("동승자");
    private final Companion pot = taxiPot(user("방장"), RECRUITING);

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("나가면 나간 시각과 함께 완주하지 못한 참여가 된다")
        void leaves() {
            CompanionParticipant participant = participant(pot, member, PENDING);

            participant.leave();

            assertThat(participant.getOutcomeStatus()).isEqualTo(INCOMPLETE);
            assertThat(participant.getLeftAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 끝난 참여는 나갈 수 없다")
        void notPending() {
            CompanionParticipant completed = participant(pot, member, COMPLETED);

            assertThatThrownBy(completed::leave).isInstanceOf(IllegalStateException.class);
            assertThat(completed.getOutcomeStatus()).isEqualTo(COMPLETED);
        }
    }

    @Nested
    @DisplayName("완주")
    class Complete {

        @Test
        @DisplayName("완주하면 나간 시각과 함께 완주한 참여가 된다")
        void completes() {
            CompanionParticipant participant = participant(pot, member, PENDING);

            participant.complete();

            assertThat(participant.isCompleted()).isTrue();
            assertThat(participant.getLeftAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 나간 참여는 완주할 수 없다")
        void notPending() {
            CompanionParticipant left = participant(pot, member, INCOMPLETE);

            assertThatThrownBy(left::complete).isInstanceOf(IllegalStateException.class);
            assertThat(left.getOutcomeStatus()).isEqualTo(INCOMPLETE);
        }
    }

    @Nested
    @DisplayName("예전 참여가 있을 때 참여")
    class Rejoin {

        @Test
        @DisplayName("나갔던 참여를 되살리면 진행 중이 되고 나간 시각이 지워진다")
        void rejoins() {
            CompanionParticipant participant = participant(pot, member, PENDING);
            participant.leave();

            CompanionParticipant rejoined = CompanionParticipant.join(pot, member, participant);

            assertThat(rejoined).isSameAs(participant);
            assertThat(participant.getOutcomeStatus()).isEqualTo(PENDING);
            assertThat(participant.getLeftAt()).isNull();
            assertThat(participant.getJoinedAt()).isNotNull();
        }

        @Test
        @DisplayName("나가지 않은 참여는 되살릴 수 없다")
        void notLeft() {
            CompanionParticipant active = participant(pot, member, PENDING);

            assertThatThrownBy(() -> CompanionParticipant.join(pot, member, active))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
