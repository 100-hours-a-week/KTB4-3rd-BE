package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.OAuthAccount;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    @Query("""
            select u.id
              from OAuthAccount a
              join a.user u
             where a.provider = :provider
               and a.providerUserId = :providerUserId
               and u.withdrawnAt is null
            """)
    Optional<Long> findActiveUserId(OAuthProvider provider, String providerUserId);
}
