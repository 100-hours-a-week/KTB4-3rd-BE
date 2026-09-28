package com.ktb.moyeota.domain.rating.service;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.rating.entity.CompanionRating;
import com.ktb.moyeota.domain.rating.error.RatingErrorCode;
import com.ktb.moyeota.domain.rating.model.RatingSubmitCommand;
import com.ktb.moyeota.domain.rating.repository.CompanionRatingRepository;
import com.ktb.moyeota.domain.rating.repository.RatingParticipantRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingParticipantRepository ratingParticipantRepository;
    private final CompanionRatingRepository companionRatingRepository;

    @Transactional
    public void submit(Long raterId, Long companionId, RatingSubmitCommand command) {
        List<CompanionParticipant> completed = ratingParticipantRepository.findCompletedParticipants(companionId);
        CompanionParticipant raterParticipation = completed.stream()
                .filter(participant -> participant.getUser().getId().equals(raterId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(RatingErrorCode.RATABLE_COMPANION_NOT_FOUND));
        if (companionRatingRepository.existsByCompanionIdAndRaterId(companionId, raterId)) {
            throw new BusinessException(RatingErrorCode.ALREADY_RATED);
        }
        Map<Long, User> ratees = completed.stream()
                .map(CompanionParticipant::getUser)
                .filter(user -> !user.getId().equals(raterId))
                .collect(Collectors.toMap(User::getId, Function.identity()));
        validateTargets(ratees.keySet(), command.ratings());

        Companion companion = raterParticipation.getCompanion();
        User rater = raterParticipation.getUser();
        List<CompanionRating> ratings = command.ratings().stream()
                .map(rating -> CompanionRating.of(companion, rater, ratees.get(rating.targetUserId()), rating.score()))
                .toList();
        try {
            companionRatingRepository.saveAllAndFlush(ratings);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RatingErrorCode.ALREADY_RATED);
        }
    }

    private static void validateTargets(Set<Long> rateeIds, List<RatingSubmitCommand.Rating> ratings) {
        Set<Long> seen = new HashSet<>();
        for (RatingSubmitCommand.Rating rating : ratings) {
            if (!rateeIds.contains(rating.targetUserId()) || !seen.add(rating.targetUserId())) {
                throw new BusinessException(RatingErrorCode.INVALID_RATING_TARGET);
            }
        }
        if (seen.size() != rateeIds.size()) {
            throw new BusinessException(RatingErrorCode.RATING_TARGET_MISSING);
        }
    }
}
