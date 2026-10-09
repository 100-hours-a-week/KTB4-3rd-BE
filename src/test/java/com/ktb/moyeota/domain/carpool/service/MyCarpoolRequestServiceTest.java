package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.carpool.model.RequestDirection.RECEIVED;
import static com.ktb.moyeota.domain.carpool.model.RequestDirection.SENT;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequestItem;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequests;
import com.ktb.moyeota.domain.carpool.model.MyRequestStatus;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({MyCarpoolRequestService.class, CarpoolRequestCursorCodec.class, MyCarpoolRequestServiceTest.Config.class})
class MyCarpoolRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private MyCarpoolRequestService myCarpoolRequestService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User me;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        me = entityManager.persist(user("나"));
    }

    @Test
    @DisplayName("보낸 요청은 최신순으로, 운전자를 상대로, 만료를 계산해 보여 준다")
    void sent() {
        Companion open = persistCarpool(RECRUITING, 1);
        CompanionRequest pending = persistRequest(open, me, CompanionRequestStatus.PENDING);
        Companion riding = persistCarpool(IN_PROGRESS, 2);
        CompanionRequest expired = persistRequest(riding, me, CompanionRequestStatus.PENDING);
        Companion other = persistCarpool(RECRUITING, 1);
        CompanionRequest rejected = persistRequest(other, me, CompanionRequestStatus.REJECTED);
        persistRequest(open, entityManager.persist(user("남")), CompanionRequestStatus.PENDING);
        flushAndClear();

        MyCarpoolRequests result = myCarpoolRequestService.find(me.getId(), SENT, null);

        assertThat(result.direction()).isEqualTo(SENT);
        assertThat(result.items()).extracting(MyCarpoolRequestItem::id)
                .containsExactly(rejected.getId(), expired.getId(), pending.getId());
        assertThat(result.items()).extracting(MyCarpoolRequestItem::status)
                .containsExactly(MyRequestStatus.REJECTED, MyRequestStatus.EXPIRED, MyRequestStatus.PENDING);
        MyCarpoolRequestItem first = result.items().getLast();
        assertThat(first.carpoolId()).isEqualTo(open.getId());
        assertThat(first.counterpart().id()).isEqualTo(host.getId());
        assertThat(first.content()).isEqualTo("같이 가요");
        assertThat(first.departureAt()).isEqualTo(DEPARTURE_AT);
        assertThat(first.createdAt()).isNotNull();
        assertThat(result.items()).allSatisfy(item -> assertThat(item.chatRoomId()).isNull());
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("수락된 요청은 내가 아직 타고 있을 때만 채팅방 id 를 준다")
    void chatRoomOnlyWhileRiding() {
        Companion riding = persistCarpool(RECRUITING, 2);
        ChatRoom ridingRoom = entityManager.persist(ChatRoom.create(riding));
        entityManager.persist(participant(riding, me, OutcomeStatus.PENDING));
        CompanionRequest stillIn = persistRequest(riding, me, CompanionRequestStatus.ACCEPTED);
        Companion left = persistCarpool(RECRUITING, 1);
        entityManager.persist(ChatRoom.create(left));
        entityManager.persist(participant(left, me, OutcomeStatus.INCOMPLETE, NOW));
        CompanionRequest leftOne = persistRequest(left, me, CompanionRequestStatus.ACCEPTED);
        Companion finished = persistCarpool(COMPLETED, 2);
        entityManager.persist(ChatRoom.create(finished));
        entityManager.persist(participant(finished, me, OutcomeStatus.COMPLETED, NOW));
        CompanionRequest finishedOne = persistRequest(finished, me, CompanionRequestStatus.ACCEPTED);
        flushAndClear();

        List<MyCarpoolRequestItem> items = myCarpoolRequestService.find(me.getId(), SENT, null).items();

        assertThat(itemOf(items, stillIn).chatRoomId()).isEqualTo(ridingRoom.getId());
        assertThat(itemOf(items, leftOne).chatRoomId()).isNull();
        assertThat(itemOf(items, finishedOne).chatRoomId()).isNull();
        assertThat(items).extracting(MyCarpoolRequestItem::status).containsOnly(MyRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("10건씩 끊고, 다음 커서로 겹치지 않게 이어서 준다")
    void pages() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(persistRequest(persistCarpool(RECRUITING, 1), me, CompanionRequestStatus.PENDING).getId());
        }
        flushAndClear();

        MyCarpoolRequests first = myCarpoolRequestService.find(me.getId(), SENT, null);
        MyCarpoolRequests second = myCarpoolRequestService.find(me.getId(), SENT, first.nextCursor());

        List<Long> newestFirst = ids.reversed();
        assertThat(first.items()).extracting(MyCarpoolRequestItem::id).containsExactlyElementsOf(newestFirst.subList(0, 10));
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.items()).extracting(MyCarpoolRequestItem::id).containsExactlyElementsOf(newestFirst.subList(10, 12));
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("받은 요청은 내 모집 중 · 출발 전 카풀의 대기 요청만, 요청자를 상대로 보여 준다")
    void received() {
        User requester = entityManager.persist(user("요청자"));
        User withdrawn = entityManager.persist(user("탈퇴자"));
        Companion mine = persistCarpool(me, RECRUITING, 1);
        CompanionRequest receivable = persistRequest(mine, requester, CompanionRequestStatus.PENDING);
        persistRequest(mine, entityManager.persist(user("거절됨")), CompanionRequestStatus.REJECTED);
        persistRequest(mine, withdrawn, CompanionRequestStatus.PENDING);
        withdrawn.withdraw(NOW);
        persistRequest(persistCarpool(me, IN_PROGRESS, 2), requester, CompanionRequestStatus.PENDING);
        Companion departed = persistCarpool(me, RECRUITING, 1);
        ReflectionTestUtils.setField(departed, "departureAt", NOW.minusMinutes(1));
        persistRequest(departed, requester, CompanionRequestStatus.PENDING);
        persistRequest(persistCarpool(host, RECRUITING, 1), requester, CompanionRequestStatus.PENDING);
        flushAndClear();

        MyCarpoolRequests result = myCarpoolRequestService.find(me.getId(), RECEIVED, null);

        assertThat(result.direction()).isEqualTo(RECEIVED);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(receivable.getId());
            assertThat(item.status()).isEqualTo(MyRequestStatus.PENDING);
            assertThat(item.counterpart().id()).isEqualTo(requester.getId());
            assertThat(item.chatRoomId()).isNull();
        });
    }

    @Test
    @DisplayName("출발 시각과 같은 순간까지는 받은 요청에 남는다")
    void receivedUntilDeparture() {
        Companion onTime = persistCarpool(me, RECRUITING, 1);
        ReflectionTestUtils.setField(onTime, "departureAt", NOW);
        persistRequest(onTime, entityManager.persist(user("요청자")), CompanionRequestStatus.PENDING);
        flushAndClear();

        assertThat(myCarpoolRequestService.find(me.getId(), RECEIVED, null).items()).hasSize(1);
    }

    @Test
    @DisplayName("잘못된 커서는 INVALID_CURSOR, 탈퇴한 사용자는 UNAUTHORIZED다")
    void errors() {
        assertThatThrownBy(() -> myCarpoolRequestService.find(me.getId(), SENT, "v1.@@@"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CURSOR);
        assertThatThrownBy(() -> myCarpoolRequestService.find(me.getId(), SENT, "abc"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CURSOR);

        me.withdraw(NOW);
        entityManager.flush();
        assertThatThrownBy(() -> myCarpoolRequestService.find(me.getId(), SENT, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        return persistCarpool(host, status, currentCount);
    }

    private Companion persistCarpool(User owner, CompanionStatus status, int currentCount) {
        Companion carpool = entityManager.persist(carpool(owner, status, currentCount));
        entityManager.persist(participant(carpool, owner, OutcomeStatus.PENDING));
        return carpool;
    }

    private CompanionRequest persistRequest(Companion carpool, User requester, CompanionRequestStatus status) {
        CompanionRequest request = CompanionRequest.send(carpool, requester, "같이 가요");
        ReflectionTestUtils.setField(request, "status", status);
        return entityManager.persist(request);
    }

    private static MyCarpoolRequestItem itemOf(List<MyCarpoolRequestItem> items, CompanionRequest request) {
        return items.stream().filter(item -> item.id().equals(request.getId())).findFirst().orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }

        @Bean
        ImageUrlResolver imageUrlResolver() {
            return new ImageUrlResolver(new S3Properties(
                    "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));
        }
    }
}
