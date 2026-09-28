package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
}
