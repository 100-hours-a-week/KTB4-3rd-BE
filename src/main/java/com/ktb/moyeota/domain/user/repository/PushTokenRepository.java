package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
}
