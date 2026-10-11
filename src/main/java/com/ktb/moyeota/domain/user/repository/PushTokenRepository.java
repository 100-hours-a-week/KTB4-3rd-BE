package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.PushToken;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    /** 사용자가 등록한 푸시 토큰 값 목록 (기기·브라우저마다 1개씩) */
    @Query("SELECT t.token FROM PushToken t WHERE t.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") Long userId);

    /** FCM이 무효(UNREGISTERED)로 응답한 토큰들을 한 번에 삭제한다 */
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    int deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);
}
