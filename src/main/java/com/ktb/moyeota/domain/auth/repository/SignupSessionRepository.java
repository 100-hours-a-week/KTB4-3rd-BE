package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupSessionRepository extends JpaRepository<SignupSession, String> {
}
