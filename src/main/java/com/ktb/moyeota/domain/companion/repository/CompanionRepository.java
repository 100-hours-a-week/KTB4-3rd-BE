package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user.id = :userId
                      and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE)
            """)
    Optional<Companion> findTaxiPotForParticipant(Long id, Long userId);

    /**
     * 동행모집 채팅 참여하기 API용.
     * "모집 마감 여부(status != RECRUITING)"와 "정원 초과 여부(currentCount >= capacity)"를
     * 애플리케이션에서 따로따로 읽어서 판단하면(체크 후 갱신), 읽은 시점과 실제 갱신 시점 사이에
     * 다른 요청이 상태/정원을 바꿔버리는 레이스 컨디션이 생길 수 있다. 그래서 두 조건 모두
     * WHERE절에 넣어서 갱신 자체를 원자적으로 만들었다. 영향받은 row가 0건이면 둘 중 하나(또는 둘 다)
     * 때문에 실패한 것 — 어느 쪽 때문인지는 실패했을 때만 서비스 계층에서 한 번 더 조회해서 판별한다.
     */
    @Modifying
    @Query("""
            UPDATE Companion c
            SET c.currentCount = c.currentCount + 1
            WHERE c.id = :id
              AND c.status = :status
              AND c.currentCount < c.capacity
            """)
    int increaseCurrentCountIfRecruitingAndNotFull(@Param("id") Long id, @Param("status") CompanionStatus status);

    /**
     * 동행모집 채팅 나가기 API용.
     */
    @Modifying
    @Query("UPDATE Companion c SET c.currentCount = c.currentCount - 1 WHERE c.id = :id AND c.currentCount > 0")
    int decreaseCurrentCount(@Param("id") Long id);
}
