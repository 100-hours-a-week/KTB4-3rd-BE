package com.ktb.moyeota.domain.rating.model;

import java.util.List;

public record RatingSubmitCommand(List<Rating> ratings) {

    public record Rating(Long targetUserId, int score) {
    }
}
