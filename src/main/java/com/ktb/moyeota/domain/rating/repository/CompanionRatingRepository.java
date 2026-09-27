package com.ktb.moyeota.domain.rating.repository;

import com.ktb.moyeota.domain.rating.entity.CompanionRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanionRatingRepository extends JpaRepository<CompanionRating, Long> {

    @Query("""
            select count(r) > 0 from CompanionRating r
             where r.companion.id = :companionId
               and r.rater.id = :raterId
            """)
    boolean existsByCompanionIdAndRaterId(Long companionId, Long raterId);
}
