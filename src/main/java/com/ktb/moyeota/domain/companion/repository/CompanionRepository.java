package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    boolean existsByHostIdAndStatusIn(Long hostId, Collection<CompanionStatus> statuses);
}
