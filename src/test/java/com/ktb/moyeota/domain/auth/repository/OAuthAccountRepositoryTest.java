package com.ktb.moyeota.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.auth.entity.OAuthAccount;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class OAuthAccountRepositoryTest {

    private static final String KAKAO_ID = "1234567890";

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("연결된 활성 회원의 ID를 찾는다")
    void findsActiveUserId() {
        User user = persistUser("길동이");
        link(user, KAKAO_ID);

        assertThat(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, KAKAO_ID))
                .contains(user.getId());
    }

    @Test
    @DisplayName("연결된 적 없는 카카오 회원번호는 빈 값이다")
    void unknownProviderUserIdIsEmpty() {
        link(persistUser("길동이"), KAKAO_ID);

        assertThat(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, "999")).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 회원은 연결이 남아 있어도 찾지 않는다")
    void withdrawnUserIsNotFound() {
        User user = persistUser("길동이");
        link(user, KAKAO_ID);
        user.withdraw(LocalDateTime.of(2026, 1, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, KAKAO_ID)).isEmpty();
    }

    @Test
    @DisplayName("같은 카카오 회원번호를 두 번 연결할 수 없다")
    void providerUserIdIsUnique() {
        link(persistUser("길동이"), KAKAO_ID);
        User another = persistUser("철수");

        assertThatThrownBy(() -> oAuthAccountRepository.saveAndFlush(
                OAuthAccount.link(another, OAuthProvider.KAKAO, KAKAO_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String nickname) {
        return entityManager.persistAndFlush(
                User.register("홍길동", nickname, Gender.MALE, null));
    }

    private void link(User user, String providerUserId) {
        entityManager.persistAndFlush(OAuthAccount.link(user, OAuthProvider.KAKAO, providerUserId));
    }
}
