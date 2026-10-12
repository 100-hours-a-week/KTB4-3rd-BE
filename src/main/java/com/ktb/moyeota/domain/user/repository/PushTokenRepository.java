package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.PushToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO push_tokens (user_id, token, created_at, updated_at)
            VALUES (:userId, :token, :now, :now) AS new
            ON DUPLICATE KEY UPDATE
                user_id = new.user_id,
                updated_at = new.updated_at
            """)
    int upsert(@Param("userId") Long userId, @Param("token") String token, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.user.id = :userId AND t.token = :token")
    int deleteByUserIdAndToken(@Param("userId") Long userId, @Param("token") String token);
}
