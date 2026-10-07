package com.ktb.moyeota.domain.user.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.auth.entity.OAuthAccount;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.domain.auth.service.OpaqueTokenFactory;
import com.ktb.moyeota.domain.auth.store.JpaSessionStore;
import com.ktb.moyeota.domain.auth.store.SessionStore;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatParticipationService;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.kakao.KakaoUnlinkClient;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({UserService.class, AuthSessionService.class, JpaSessionStore.class, OpaqueTokenFactory.class,
        ChatParticipationService.class, ChatSystemMessageService.class, UserWithdrawalTest.FixedClock.class})
@EnableConfigurationProperties(AuthProperties.class)
class UserWithdrawalTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final String KAKAO_USER_ID = "1234567890";

    @Autowired
    private UserService userService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private KakaoUnlinkClient kakaoUnlinkClient;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private SignupSessionStore signupSessionStore;

    @MockitoBean
    private AccountNoCipher accountNoCipher;

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @MockitoBean
    private ImageUrlResolver imageUrlResolver;

    private User me;

    @BeforeEach
    void setUp() {
        me = entityManager.persist(bankAccountHolder("탈퇴자"));
        entityManager.persist(OAuthAccount.link(me, OAuthProvider.KAKAO, KAKAO_USER_ID));
        sessionStore.create(me.getId(), "hash-device-a", NOW.plusDays(7));
        sessionStore.create(me.getId(), "hash-device-b", NOW.plusDays(7));
    }

    @Test
    @DisplayName("탈퇴하면 실명·닉네임을 익명화하고 계좌를 파기하며 카카오 연결 정보와 모든 세션을 지운다")
    void withdraw() {
        entityManager.flush();
        entityManager.clear();

        userService.withdraw(me.getId());

        User withdrawn = entityManager.find(User.class, me.getId());
        assertThat(withdrawn.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(withdrawn.getName()).isEqualTo("탈퇴한사용자_" + me.getId());
        assertThat(withdrawn.getNickname()).isEqualTo("탈퇴한사용자_" + me.getId());
        assertThat(withdrawn.hasBankAccount()).isFalse();
        assertThat(oAuthAccountRepository.findByUserId(me.getId())).isEmpty();
        assertThat(sessionStore.findToken("hash-device-a")).isEmpty();
        assertThat(sessionStore.findToken("hash-device-b")).isEmpty();
        verify(kakaoUnlinkClient).unlink(KAKAO_USER_ID);
    }

    @Test
    @DisplayName("다른 사용자의 카카오 연결 정보와 세션은 남는다")
    void keepsOtherUsers() {
        User other = entityManager.persist(user("다른사람"));
        entityManager.persist(OAuthAccount.link(other, OAuthProvider.KAKAO, "999"));
        sessionStore.create(other.getId(), "hash-other", NOW.plusDays(7));

        userService.withdraw(me.getId());

        assertThat(oAuthAccountRepository.findByUserId(other.getId())).hasSize(1);
        assertThat(sessionStore.findToken("hash-other")).isPresent();
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자는 다시 처리하지 않고 끝난다")
    void alreadyWithdrawn() {
        me.withdraw(NOW.minusDays(1));
        entityManager.flush();

        userService.withdraw(me.getId());

        assertThat(entityManager.find(User.class, me.getId()).getWithdrawnAt()).isEqualTo(NOW.minusDays(1));
        assertThat(sessionStore.findToken("hash-device-a")).isPresent();
        verifyNoInteractions(kakaoUnlinkClient);
    }

    @Test
    @DisplayName("없는 사용자는 401이다")
    void unknownUser() {
        assertThatThrownBy(() -> userService.withdraw(me.getId() + 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CompanionStatus.class, names = {"RECRUITING", "IN_PROGRESS"})
    @DisplayName("진행 중인 택시팟의 방장이면 탈퇴할 수 없다")
    void activeTaxiPotHost(CompanionStatus status) {
        entityManager.persist(taxiPot(me, status));

        assertRejected(UserErrorCode.ACTIVE_HOST_EXISTS);
    }

    @Test
    @DisplayName("모집 중인 동행모집의 방장이면 탈퇴할 수 없다")
    void activeCompanionPostHost() {
        entityManager.persist(companionPost(me));

        assertRejected(UserErrorCode.ACTIVE_HOST_EXISTS);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CompanionStatus.class, names = {"COMPLETED", "CANCELED"})
    @DisplayName("끝난 동행의 방장은 탈퇴할 수 있다")
    void finishedHost(CompanionStatus status) {
        entityManager.persist(taxiPot(me, status));

        userService.withdraw(me.getId());

        assertThat(entityManager.find(User.class, me.getId()).isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("택시팟에 참여 중이면 탈퇴할 수 없다")
    void activeTaxiPotParticipant() {
        Companion pot = entityManager.persist(taxiPot(entityManager.persist(user("방장")), CompanionStatus.IN_PROGRESS));
        entityManager.persist(participant(pot, me, PENDING));

        assertRejected(UserErrorCode.ACTIVE_TAXI_POT_EXISTS);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = OutcomeStatus.class, names = {"COMPLETED", "INCOMPLETE"})
    @DisplayName("택시팟을 완주했거나 나갔으면 탈퇴할 수 있다")
    void finishedTaxiPotParticipant(OutcomeStatus outcome) {
        Companion pot = entityManager.persist(taxiPot(entityManager.persist(user("방장")), CompanionStatus.IN_PROGRESS));
        entityManager.persist(participant(pot, me, outcome));

        userService.withdraw(me.getId());

        assertThat(entityManager.find(User.class, me.getId()).isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("모집 중인 동행모집에 참여 중이면 채팅방에서 나가고 탈퇴한다")
    void leavesCompanionPostChat() {
        Companion post = entityManager.persist(companionPost(entityManager.persist(user("방장"))));
        entityManager.persist(ChatRoom.create(post));
        CompanionParticipant joined = entityManager.persist(participant(post, me, PENDING));

        userService.withdraw(me.getId());

        assertThat(entityManager.find(User.class, me.getId()).isWithdrawn()).isTrue();
        assertThat(entityManager.find(CompanionParticipant.class, joined.getId()).getLeftAt()).isNotNull();
        assertThat(leaveMessageCount(post)).isEqualTo(1);
    }

    @Test
    @DisplayName("끝난 동행모집의 채팅방에서는 나가지 않는다")
    void keepsFinishedCompanionPostChat() {
        Companion post = entityManager.persist(companionPost(entityManager.persist(user("방장"))));
        ReflectionTestUtils.setField(post, "status", CompanionStatus.COMPLETED);
        entityManager.persist(ChatRoom.create(post));
        CompanionParticipant joined = entityManager.persist(participant(post, me, PENDING));

        userService.withdraw(me.getId());

        assertThat(entityManager.find(CompanionParticipant.class, joined.getId()).getLeftAt()).isNull();
        assertThat(leaveMessageCount(post)).isZero();
    }

    private long leaveMessageCount(Companion post) {
        return entityManager.getEntityManager()
                .createQuery("""
                        select count(m) from Message m
                         where m.chatRoom.companion = :post
                           and m.messageType = :type
                        """, Long.class)
                .setParameter("post", post)
                .setParameter("type", MessageType.SYSTEM_LEAVE)
                .getSingleResult();
    }

    private void assertRejected(UserErrorCode errorCode) {
        assertThatThrownBy(() -> userService.withdraw(me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);

        assertThat(entityManager.find(User.class, me.getId()).isWithdrawn()).isFalse();
        assertThat(oAuthAccountRepository.findByUserId(me.getId())).hasSize(1);
        assertThat(sessionStore.findToken("hash-device-a")).isPresent();
        verifyNoInteractions(kakaoUnlinkClient);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
