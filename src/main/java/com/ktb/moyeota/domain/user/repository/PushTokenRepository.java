package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.PushToken;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    @Query("SELECT t.token FROM PushToken t WHERE t.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    int deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);
}
