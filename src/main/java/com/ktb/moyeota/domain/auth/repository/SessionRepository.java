package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {
}
