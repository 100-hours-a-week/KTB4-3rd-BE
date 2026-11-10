package com.ktb.moyeota.domain.user.service;

import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.MyCar;
import com.ktb.moyeota.domain.user.model.OwnedCarCommand;
import com.ktb.moyeota.domain.user.repository.OwnedCarRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnedCarService {

    private final OwnedCarRepository ownedCarRepository;
    private final UserRepository userRepository;

    @Transactional
    public MyCar register(Long userId, OwnedCarCommand command) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (ownedCarRepository.existsByUserId(userId)) {
            throw new BusinessException(UserErrorCode.CAR_ALREADY_REGISTERED);
        }
        return MyCar.from(ownedCarRepository.save(OwnedCar.register(user, command.model(), command.number())));
    }

    @Transactional
    public MyCar update(Long userId, Long carId, OwnedCarCommand command) {
        OwnedCar car = findMine(userId, carId);
        car.change(command.model(), command.number());
        ownedCarRepository.flush();
        return MyCar.from(car);
    }

    @Transactional
    public void delete(Long userId, Long carId) {
        ownedCarRepository.delete(findMine(userId, carId));
    }

    @Transactional(readOnly = true)
    public List<MyCar> findAll(Long userId) {
        return ownedCarRepository.findAllByUserIdOrderByIdAsc(userId).stream().map(MyCar::from).toList();
    }

    private OwnedCar findMine(Long userId, Long carId) {
        return ownedCarRepository.findByIdAndUserId(carId, userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.CAR_NOT_FOUND));
    }
}
