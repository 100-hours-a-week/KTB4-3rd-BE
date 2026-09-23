package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SignupSessionRepository extends JpaRepository<SignupSession, String> {

    @Query("""
            select new com.ktb.moyeota.domain.auth.model.SignupSessionView(
                       s.tokenHash, s.provider, s.providerUserId, s.name, s.expiresAt)
              from SignupSession s
             where s.tokenHash = :tokenHash
            """)
    Optional<SignupSessionView> findViewByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from SignupSession s where s.tokenHash = :tokenHash")
    void deleteByTokenHash(String tokenHash);
}
