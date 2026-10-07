package com.ktb.moyeota.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.MyCar;
import com.ktb.moyeota.domain.user.model.OwnedCarCommand;
import com.ktb.moyeota.domain.user.repository.OwnedCarRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.fixture.UserFixture;
import com.ktb.moyeota.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class OwnedCarServiceTest {

    @Autowired
    private OwnedCarRepository ownedCarRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private OwnedCarService service;
    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        service = new OwnedCarService(ownedCarRepository, userRepository);
        owner = userRepository.saveAndFlush(UserFixture.user("운전자"));
        other = userRepository.saveAndFlush(UserFixture.user("다른사람"));
    }

    @Test
    @DisplayName("차량이 없으면 내 차량으로 등록한다")
    void register() {
        MyCar registered = service.register(owner.getId(), new OwnedCarCommand("아반떼", "12가3456"));

        entityManager.flush();
        entityManager.clear();
        assertThat(registered.createdAt()).isNotNull();
        assertThat(ownedCarRepository.findAll()).singleElement().satisfies(car -> {
            assertThat(car.getId()).isEqualTo(registered.id());
            assertThat(car.getUser().getId()).isEqualTo(owner.getId());
            assertThat(car.getModel()).isEqualTo("아반떼");
            assertThat(car.getNumber()).isEqualTo("12가3456");
        });
    }

    @Test
    @DisplayName("이미 차량이 있으면 등록하지 못하고 기존 차량만 남는다")
    void registerSecondCar() {
        Long carId = ownedCarRepository.saveAndFlush(OwnedCar.register(owner, "아반떼", "12가3456")).getId();

        assertThatThrownBy(() -> service.register(owner.getId(), new OwnedCarCommand("쏘나타", "34나5678")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.CAR_ALREADY_REGISTERED);

        entityManager.flush();
        entityManager.clear();
        assertThat(ownedCarRepository.findAll()).singleElement()
                .extracting(OwnedCar::getId).isEqualTo(carId);
    }

    @Test
    @DisplayName("다른 사람에게 차량이 있어도 내 차량은 등록한다")
    void registerWhileOthersHaveCar() {
        ownedCarRepository.saveAndFlush(OwnedCar.register(other, "모닝", "56다7890"));

        MyCar registered = service.register(owner.getId(), new OwnedCarCommand("아반떼", "12가3456"));

        entityManager.flush();
        entityManager.clear();
        assertThat(ownedCarRepository.findById(registered.id()).orElseThrow().getUser().getId())
                .isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("내 차량을 수정하면 같은 행의 값과 수정 시각이 바뀐다")
    void updateMine() {
        OwnedCar car = ownedCarRepository.saveAndFlush(OwnedCar.register(owner, "아반떼", "12가3456"));

        MyCar updated = service.update(owner.getId(), car.getId(), new OwnedCarCommand("쏘나타", "34나5678"));

        entityManager.clear();
        assertThat(updated.id()).isEqualTo(car.getId());
        OwnedCar saved = ownedCarRepository.findById(car.getId()).orElseThrow();
        assertThat(saved.getModel()).isEqualTo("쏘나타");
        assertThat(saved.getNumber()).isEqualTo("34나5678");
        assertThat(updated.updatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("다른 사람의 차량은 수정하지 못하고 값도 그대로다")
    void updateOthers() {
        OwnedCar car = ownedCarRepository.saveAndFlush(OwnedCar.register(other, "아반떼", "12가3456"));

        assertThatThrownBy(() -> service.update(owner.getId(), car.getId(), new OwnedCarCommand("쏘나타", "34나5678")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(UserErrorCode.CAR_NOT_FOUND);

        entityManager.clear();
        assertThat(ownedCarRepository.findById(car.getId()).orElseThrow().getModel()).isEqualTo("아반떼");
    }

    @Test
    @DisplayName("내 차량을 삭제하면 행이 사라진다")
    void deleteMine() {
        OwnedCar car = ownedCarRepository.saveAndFlush(OwnedCar.register(owner, "아반떼", "12가3456"));

        service.delete(owner.getId(), car.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(ownedCarRepository.findById(car.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사람의 차량은 삭제하지 못하고 행이 남는다")
    void deleteOthers() {
        OwnedCar car = ownedCarRepository.saveAndFlush(OwnedCar.register(other, "아반떼", "12가3456"));

        assertThatThrownBy(() -> service.delete(owner.getId(), car.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(UserErrorCode.CAR_NOT_FOUND);

        entityManager.flush();
        entityManager.clear();
        assertThat(ownedCarRepository.findById(car.getId())).isPresent();
    }

    @Test
    @DisplayName("내 차량만 돌려준다")
    void findAllMine() {
        ownedCarRepository.saveAndFlush(OwnedCar.register(owner, "아반떼", "12가3456"));
        ownedCarRepository.saveAndFlush(OwnedCar.register(other, "모닝", "56다7890"));

        List<MyCar> cars = service.findAll(owner.getId());

        assertThat(cars).extracting(MyCar::model).containsExactly("아반떼");
    }

    @Test
    @DisplayName("차량이 없으면 빈 목록이다")
    void findAllNone() {
        ownedCarRepository.saveAndFlush(OwnedCar.register(other, "모닝", "56다7890"));

        assertThat(service.findAll(owner.getId())).isEmpty();
    }
}
