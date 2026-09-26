package com.ktb.moyeota.domain.companionpost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateRequest;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateResponse;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostDetailResponse;
import com.ktb.moyeota.domain.companionpost.exception.CompanionPostErrorCode;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class CompanionPostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COMPANION_ID = 20L;

    @Mock
    private CompanionPostRepository companionPostRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private EntityManager entityManager;

    private CompanionPostService service;

    @BeforeEach
    void setUp() {
        service = new CompanionPostService(companionPostRepository, chatRoomService, entityManager);
    }

    private User activeUser() {
        return User.register("우림", "rain", Gender.FEMALE, null);
    }

    private User withdrawnUser() {
        User user = User.register("탈퇴자", "withdrawn", Gender.FEMALE, null);
        user.withdraw(LocalDateTime.now());
        return user;
    }

    private CompanionPostCreateRequest requestWith(
            LocalDateTime departureAt, TransportType transportType, Integer recruitCount,
            BigDecimal originLat, BigDecimal originLng, BigDecimal destLat, BigDecimal destLng) {
        return new CompanionPostCreateRequest(
                "판교역", originLat, originLng,
                "강남역", destLat, destLng,
                departureAt, transportType, recruitCount, "같이 타실 분 구합니다!");
    }

    private CompanionPostCreateRequest validRequest() {
        return requestWith(
                LocalDateTime.now().plusHours(1), TransportType.TAXI, 3,
                BigDecimal.valueOf(37.3945), BigDecimal.valueOf(127.1112),
                BigDecimal.valueOf(37.4979), BigDecimal.valueOf(127.0276));
    }

    @Nested
    @DisplayName("동행모집 게시글 등록")
    class Create {

        @Test
        @DisplayName("정상 요청이면 방장 포함 총원(recruitCount+1)으로 저장한다")
        void createsCompanion() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());

            CompanionPostCreateResponse response = service.create(USER_ID, validRequest());

            ArgumentCaptor<Companion> captor = ArgumentCaptor.forClass(Companion.class);
            verify(companionPostRepository).save(captor.capture());
            assertThat(captor.getValue().getCapacity()).isEqualTo(4);
            // assertThat(response.chatRoomId()).isNull(); // TODO: Chat 도메인 연동 전까지 null
        }

        @Test
        @DisplayName("탈퇴한 유저면 401을 던지고 저장하지 않는다")
        void rejectsWithdrawnUser() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(withdrawnUser());

            assertThatThrownBy(() -> service.create(USER_ID, validRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.UNAUTHORIZED);

            verify(companionPostRepository, never()).save(any());
        }

        @Test
        @DisplayName("출발 시각이 과거면 422를 던진다")
        void rejectsPastDeparture() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            CompanionPostCreateRequest request = requestWith(
                    LocalDateTime.now().minusHours(1), TransportType.TAXI, 3,
                    BigDecimal.valueOf(37.3945), BigDecimal.valueOf(127.1112),
                    BigDecimal.valueOf(37.4979), BigDecimal.valueOf(127.0276));

            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CompanionPostErrorCode.COMPANION_POST_DEPARTURE_AT_PAST);

            verify(companionPostRepository, never()).save(any());
        }

        @Test
        @DisplayName("출발지와 도착지가 같으면 422를 던진다")
        void rejectsSameOriginAndDest() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            BigDecimal lat = BigDecimal.valueOf(37.3945);
            BigDecimal lng = BigDecimal.valueOf(127.1112);
            CompanionPostCreateRequest request = requestWith(
                    LocalDateTime.now().plusHours(1), TransportType.TAXI, 3, lat, lng, lat, lng);

            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CompanionPostErrorCode.COMPANION_POST_ORIGIN_DEST_SAME);
        }

        @Test
        @DisplayName("택시/자차는 방장 제외 3명 초과면 422를 던진다")
        void rejectsRecruitCountOverLimitForTaxi() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            CompanionPostCreateRequest request = requestWith(
                    LocalDateTime.now().plusHours(1), TransportType.TAXI, 4,
                    BigDecimal.valueOf(37.3945), BigDecimal.valueOf(127.1112),
                    BigDecimal.valueOf(37.4979), BigDecimal.valueOf(127.0276));

            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CompanionPostErrorCode.COMPANION_POST_RECRUIT_COUNT_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("버스/지하철은 방장 제외 9명까지 허용한다")
        void allowsUpToNineForBus() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            CompanionPostCreateRequest request = requestWith(
                    LocalDateTime.now().plusHours(1), TransportType.BUS, 9,
                    BigDecimal.valueOf(37.3945), BigDecimal.valueOf(127.1112),
                    BigDecimal.valueOf(37.4979), BigDecimal.valueOf(127.0276));

            service.create(USER_ID, request);

            ArgumentCaptor<Companion> captor = ArgumentCaptor.forClass(Companion.class);
            verify(companionPostRepository).save(captor.capture());
            assertThat(captor.getValue().getCapacity()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("동행모집 상세 조회")
    class Find {

        @Test
        @DisplayName("존재하지 않으면 404를 던진다")
        void throwsNotFoundWhenMissing() {
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.find(USER_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CompanionPostErrorCode.COMPANION_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("취소된 게시글이면 410을 던진다")
        void throwsGoneWhenCanceled() {
            Companion companion = mock(Companion.class);
            given(companion.getStatus()).willReturn(CompanionStatus.CANCELED);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            assertThatThrownBy(() -> service.find(USER_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CompanionPostErrorCode.COMPANION_POST_CANCELED);
        }

        private Companion companionWith(LocalDateTime departureAt, int currentCount, int capacity, Long hostId) {
            User host = mock(User.class);
            given(host.getId()).willReturn(hostId);
            given(host.getNickname()).willReturn("호스트닉네임");

            Companion companion = mock(Companion.class);
            given(companion.getId()).willReturn(COMPANION_ID);
            given(companion.getStatus()).willReturn(CompanionStatus.RECRUITING);
            given(companion.getCurrentCount()).willReturn(currentCount);
            given(companion.getCapacity()).willReturn(capacity);
            given(companion.getOriginName()).willReturn("판교역");
            given(companion.getDestName()).willReturn("강남역");
            given(companion.getDepartureAt()).willReturn(departureAt);
            given(companion.getTransportType()).willReturn(TransportType.TAXI);
            given(companion.getContent()).willReturn("내용");
            given(companion.getHost()).willReturn(host);
            return companion;
        }

        @Test
        @DisplayName("출발 전이고 정원이 남았으면 isExpired=false, isFull=false다")
        void beforeDepartureWithRoomLeft() {
            Companion companion = companionWith(LocalDateTime.now().plusHours(1), 2, 4, 99L);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            CompanionPostDetailResponse response = service.find(USER_ID, COMPANION_ID);

            assertThat(response.isExpired()).isFalse();
            assertThat(response.isFull()).isFalse();
            assertThat(response.title()).isEqualTo("판교역 → 강남역");
        }

        @Test
        @DisplayName("정원이 다 찼으면 isFull=true다")
        void full() {
            Companion companion = companionWith(LocalDateTime.now().plusHours(1), 4, 4, 99L);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            CompanionPostDetailResponse response = service.find(USER_ID, COMPANION_ID);

            assertThat(response.isFull()).isTrue();
        }

        @Test
        @DisplayName("출발 시각이 지났으면 isExpired=true다")
        void afterDepartureIsExpired() {
            Companion companion = companionWith(LocalDateTime.now().minusHours(1), 2, 4, 99L);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            CompanionPostDetailResponse response = service.find(USER_ID, COMPANION_ID);

            assertThat(response.isExpired()).isTrue();
        }

        @Test
        @DisplayName("방장이 조회하면 joined=true다")
        void hostSeesJoinedTrue() {
            Companion companion = companionWith(LocalDateTime.now().plusHours(1), 2, 4, USER_ID);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            CompanionPostDetailResponse response = service.find(USER_ID, COMPANION_ID);

            assertThat(response.joined()).isTrue();
        }

        @Test
        @DisplayName("방장이 아니면 joined=false다")
        void nonHostSeesJoinedFalse() {
            Companion companion = companionWith(LocalDateTime.now().plusHours(1), 2, 4, 99L);
            given(companionPostRepository.findCompanionPostById(COMPANION_ID)).willReturn(Optional.of(companion));

            CompanionPostDetailResponse response = service.find(USER_ID, COMPANION_ID);

            assertThat(response.joined()).isFalse();
        }
    }
}
