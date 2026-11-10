package com.ktb.moyeota.domain.carpool.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class CompanionRequestRepositoryTest {

    @Autowired
    private CompanionRequestRepository companionRequestRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Companion carpool;
    private User requester;

    @BeforeEach
    void setUp() {
        User host = entityManager.persist(user("방장"));
        carpool = entityManager.persist(carpool(host, RECRUITING, 1));
        requester = entityManager.persistAndFlush(user("요청자"));
    }

    @Test
    @DisplayName("요청을 보내면 대기 상태로 내용 · 요청 시각과 함께 저장된다")
    void sends() {
        Long id = companionRequestRepository.saveAndFlush(
                CompanionRequest.send(carpool, requester, "판교역에서 같이 가고 싶습니다!")).getId();

        entityManager.clear();
        CompanionRequest saved = companionRequestRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CompanionRequestStatus.PENDING);
        assertThat(saved.getContent()).isEqualTo("판교역에서 같이 가고 싶습니다!");
        assertThat(saved.getCompanion().getId()).isEqualTo(carpool.getId());
        assertThat(saved.getRequester().getId()).isEqualTo(requester.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(pendingRequesterIdOf(id)).isEqualTo(requester.getId());
    }

    @Test
    @DisplayName("같은 카풀에 대기 중인 요청이 있으면 같은 사람의 두 번째 요청은 DB 가 막는다")
    void rejectsSecondPendingRequest() {
        companionRequestRepository.saveAndFlush(CompanionRequest.send(carpool, requester, "첫 요청"));

        assertThatThrownBy(() -> companionRequestRepository.saveAndFlush(
                CompanionRequest.send(carpool, requester, "두 번째 요청")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("처리된 요청은 중복 검사에서 빠져 다시 요청할 수 있고, 처리된 요청끼리는 여러 개 남는다")
    void allowsRequestAfterHandled() {
        Long rejected = companionRequestRepository.saveAndFlush(
                CompanionRequest.send(carpool, requester, "첫 요청")).getId();
        changeStatus(rejected, CompanionRequestStatus.REJECTED);
        Long rejectedAgain = companionRequestRepository.saveAndFlush(
                CompanionRequest.send(carpool, requester, "두 번째 요청")).getId();
        changeStatus(rejectedAgain, CompanionRequestStatus.REJECTED);

        Long pending = companionRequestRepository.saveAndFlush(
                CompanionRequest.send(carpool, requester, "세 번째 요청")).getId();

        assertThat(pendingRequesterIdOf(rejected)).isNull();
        assertThat(pendingRequesterIdOf(rejectedAgain)).isNull();
        assertThat(pendingRequesterIdOf(pending)).isEqualTo(requester.getId());
    }

    @Test
    @DisplayName("다른 사람의 대기 요청이나 다른 카풀의 대기 요청과는 겹치지 않는다")
    void allowsOtherRequesterAndOtherCarpool() {
        User other = entityManager.persist(user("다른요청자"));
        Companion otherCarpool = entityManager.persistAndFlush(carpool(other, RECRUITING, 1));
        companionRequestRepository.saveAndFlush(CompanionRequest.send(carpool, requester, "요청"));

        companionRequestRepository.saveAndFlush(CompanionRequest.send(carpool, other, "다른 사람 요청"));
        companionRequestRepository.saveAndFlush(CompanionRequest.send(otherCarpool, requester, "다른 카풀 요청"));

        assertThat(companionRequestRepository.count()).isEqualTo(3);
    }

    private void changeStatus(Long requestId, CompanionRequestStatus status) {
        entityManager.getEntityManager()
                .createQuery("UPDATE CompanionRequest r SET r.status = :status WHERE r.id = :id")
                .setParameter("status", status)
                .setParameter("id", requestId)
                .executeUpdate();
    }

    private Long pendingRequesterIdOf(Long requestId) {
        Object value = entityManager.getEntityManager()
                .createNativeQuery("SELECT pending_requester_id FROM companion_requests WHERE id = :id")
                .setParameter("id", requestId)
                .getSingleResult();
        return value == null ? null : ((Number) value).longValue();
    }
}
