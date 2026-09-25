package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.ParticipantOutcome;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class ParticipantFixture {

    public static final LocalDateTime JOINED_AT = CompanionFixture.DEPARTURE_AT.minusHours(1);

    private ParticipantFixture() {
    }

    public static CompanionParticipant participant(Companion companion, User user, ParticipantOutcome outcome) {
        CompanionParticipant participant = CompanionParticipant.join(companion, user, JOINED_AT);
        ReflectionTestUtils.setField(participant, "outcomeStatus", outcome);
        return participant;
    }
}
