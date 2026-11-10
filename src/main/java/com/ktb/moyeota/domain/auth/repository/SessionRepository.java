package com.ktb.moyeota.domain.auth.repository;

import com.ktb.moyeota.domain.auth.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, Long> {

    @Modifying
    @Query("delete from Session s where s.userId = :userId")
    void deleteByUserId(Long userId);
}
