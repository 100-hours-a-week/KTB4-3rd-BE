package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class ParticipantFixture {

    private ParticipantFixture() {
    }

    public static CompanionParticipant participant(Companion companion, User user, OutcomeStatus outcome) {
        CompanionParticipant participant = CompanionParticipant.join(companion, user);
        ReflectionTestUtils.setField(participant, "outcomeStatus", outcome);
        return participant;
    }

    public static CompanionParticipant participant(
            Companion companion, User user, OutcomeStatus outcome, LocalDateTime leftAt) {
        CompanionParticipant participant = participant(companion, user, outcome);
        ReflectionTestUtils.setField(participant, "leftAt", leftAt);
        return participant;
    }

    public static CompanionParticipant participant(Companion companion, User user, LocalDateTime joinedAt) {
        CompanionParticipant participant = CompanionParticipant.join(companion, user);
        ReflectionTestUtils.setField(participant, "joinedAt", joinedAt);
        return participant;
    }
}
