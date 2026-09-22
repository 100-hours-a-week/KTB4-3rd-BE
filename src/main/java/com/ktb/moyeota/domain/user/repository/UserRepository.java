package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);
}
