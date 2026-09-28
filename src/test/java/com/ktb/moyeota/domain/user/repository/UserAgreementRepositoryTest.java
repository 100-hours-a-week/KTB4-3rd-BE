package com.ktb.moyeota.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.entity.UserAgreement;
import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserAgreementRepositoryTest {

    private static final LocalDateTime SIGNED_UP_AT = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Autowired
    private UserAgreementRepository userAgreementRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("필수 약관은 동의 시각 하나와 현재 약관 버전으로 기록한다")
    void recordsRequiredAgreementAsTimestamp() {
        UserAgreement saved = saveAndReload(persistUser("길동이"), new AgreementsCommand(false, false));

        assertThat(saved.getRequiredAgreedAt()).isEqualTo(SIGNED_UP_AT);
        assertThat(saved.getTermVersion()).isEqualTo(UserAgreement.CURRENT_TERM_VERSION);
    }

    @Test
    @DisplayName("선택 약관은 받은 값 그대로 저장한다")
    void storesOptionalAgreementsAsGiven() {
        UserAgreement saved = saveAndReload(persistUser("길동이"), new AgreementsCommand(true, false));

        assertThat(saved.isAccountThirdPartyAgreed()).isTrue();
        assertThat(saved.isMarketingAgreed()).isFalse();
    }

    @Test
    @DisplayName("선택 약관의 변경 시각은 동의하지 않았어도 가입 시각이다")
    void changedAtIsSignupTimeEvenWhenDeclined() {
        UserAgreement saved = saveAndReload(persistUser("길동이"), new AgreementsCommand(false, false));

        assertThat(saved.getAccountThirdPartyChangedAt()).isEqualTo(SIGNED_UP_AT);
        assertThat(saved.getMarketingChangedAt()).isEqualTo(SIGNED_UP_AT);
    }

    @Test
    @DisplayName("사용자 한 명에 약관 동의는 한 행이다")
    void oneRowPerUser() {
        User user = persistUser("길동이");
        userAgreementRepository.saveAndFlush(
                UserAgreement.agree(user, new AgreementsCommand(false, false), SIGNED_UP_AT));

        assertThatThrownBy(() -> userAgreementRepository.saveAndFlush(
                UserAgreement.agree(user, new AgreementsCommand(true, true), SIGNED_UP_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserAgreement saveAndReload(User user, AgreementsCommand agreements) {
        Long id = userAgreementRepository.saveAndFlush(
                UserAgreement.agree(user, agreements, SIGNED_UP_AT)).getId();
        entityManager.clear();
        return userAgreementRepository.findById(id).orElseThrow();
    }

    private User persistUser(String nickname) {
        return entityManager.persistAndFlush(User.register("홍길동", nickname, Gender.MALE, null));
    }
}
