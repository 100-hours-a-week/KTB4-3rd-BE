package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.repository.CompanionParticipantRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxiPotServiceTest {

    private static final Long USER_ID = 42L;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @InjectMocks
    private TaxiPotService service;

    @Test
    @DisplayName("진행 중인 택시팟이 있으면 조회된다")
    void findsCurrent() {
        given(companionParticipantRepository.findCurrentTaxiPot(USER_ID))
                .willReturn(Optional.of(taxiPot(user("길동이"), CompanionStatus.RECRUITING)));

        assertThat(service.findMyCurrent(USER_ID)).isPresent();
    }

    @Test
    @DisplayName("진행 중인 택시팟이 없으면 비어 있다")
    void emptyWhenNone() {
        given(companionParticipantRepository.findCurrentTaxiPot(USER_ID)).willReturn(Optional.empty());

        assertThat(service.findMyCurrent(USER_ID)).isEmpty();
    }
}
