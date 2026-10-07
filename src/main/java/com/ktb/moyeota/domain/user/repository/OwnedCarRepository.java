package com.ktb.moyeota.domain.user.repository;

import com.ktb.moyeota.domain.user.entity.OwnedCar;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnedCarRepository extends JpaRepository<OwnedCar, Long> {

    boolean existsByUserId(Long userId);

    Optional<OwnedCar> findByIdAndUserId(Long id, Long userId);

    List<OwnedCar> findAllByUserIdOrderByIdAsc(Long userId);
}
