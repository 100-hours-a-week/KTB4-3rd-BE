package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.OwnedCarRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CarpoolDetailReader {

    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final OwnedCarRepository ownedCarRepository;
    private final ImageUrlResolver imageUrlResolver;

    public CarpoolDetail read(Companion carpool) {
        User host = carpool.getHost();
        String carModel = ownedCarRepository.findAllByUserIdOrderByIdAsc(host.getId()).stream()
                .findFirst()
                .map(OwnedCar::getModel)
                .orElse(null);
        List<Member> participants = carpoolParticipantRepository.findRiders(carpool.getId()).stream()
                .map(participant -> toMember(participant.getUser()))
                .toList();
        return new CarpoolDetail(
                carpool.getId(),
                carpool.getStatus(),
                toMember(host),
                carpool.getOriginName(),
                carpool.getDestName(),
                carpool.getDepartureAt(),
                carModel,
                carpool.getCurrentCount(),
                carpool.getCapacity(),
                carpool.getCurrentCount() >= carpool.getCapacity(),
                participants);
    }

    private Member toMember(User user) {
        return new Member(user.getId(), user.getName(), imageUrlResolver.toUrl(user.getProfileImageUrl()));
    }
}
