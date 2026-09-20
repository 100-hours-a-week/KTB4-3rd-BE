package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.SessionToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTokenRepository extends JpaRepository<SessionToken, String> {
}
