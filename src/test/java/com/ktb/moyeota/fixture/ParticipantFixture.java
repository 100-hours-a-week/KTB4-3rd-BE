package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 2026-09-26: CompanionParticipant 엔티티가 채팅 도메인과 companion 도메인에 중복 구현돼 있던 걸
 * 채팅 도메인 것(domain.chat.entity.CompanionParticipant)으로 일원화하면서 이 픽스처도 같이 옮김.
 * joinedAt은 채팅 도메인 엔티티에서 @PrePersist로만 설정되므로(생성자 파라미터 없음) 여기서
 * 직접 지정하지 않는다 — 실제 persist가 일어나는 @DataJpaTest에서는 자동으로 채워진다.
 */
public final class ParticipantFixture {

    private ParticipantFixture() {
    }

    public static CompanionParticipant participant(Companion companion, User user, OutcomeStatus outcome) {
        CompanionParticipant participant = CompanionParticipant.join(companion, user);
        ReflectionTestUtils.setField(participant, "outcomeStatus", outcome);
        return participant;
    }
}
