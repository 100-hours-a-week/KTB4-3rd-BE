package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.SessionToken;
import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionTokenRepository extends JpaRepository<SessionToken, String> {

    @Query("""
            select new com.ktb.moyeota.domain.auth.model.SessionTokenView(
                       t.tokenHash, s.id, s.userId, t.supersededAt, s.absoluteExpiresAt)
              from SessionToken t
              join t.session s
             where t.tokenHash = :tokenHash
            """)
    Optional<SessionTokenView> findViewByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update SessionToken t
               set t.supersededAt = :now
             where t.tokenHash = :tokenHash
               and t.supersededAt is null
            """)
    int markSuperseded(String tokenHash, LocalDateTime now);

    @Modifying
    @Query("delete from SessionToken t where t.session.id = :sessionId")
    void deleteBySessionId(Long sessionId);
}
