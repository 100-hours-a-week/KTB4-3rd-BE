package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.config.CongestionSpotProperties;
import com.ktb.moyeota.domain.congestion.document.Signal;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.message.ChangedComment;
import com.ktb.moyeota.domain.congestion.message.ChangedPost;
import com.ktb.moyeota.domain.congestion.message.PostChangedData;
import com.ktb.moyeota.domain.congestion.repository.SignalRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CongestionSignalService {

    private static final ZoneId MESSAGE_ZONE = ZoneId.of("Asia/Seoul");

    private final SpotRepository spotRepository;
    private final SignalRepository signalRepository;
    private final CongestionSpotProperties spotProperties;

    public SignalLoadOutcome load(PostChangedData data) {
        ChangedPost post = data.post();
        Optional<Spot> spot = spotRepository.findNearest(
                post.lat().doubleValue(), post.lng().doubleValue(), spotProperties.radiusMeters());
        if (spot.isEmpty()) {
            return SignalLoadOutcome.OUT_OF_RANGE;
        }
        Long spotId = spot.get().getId();
        Signal postSignal = Signal.ofPost(post.postId(), spotId, authorKey(post.authorId()),
                toInstant(post.createdAt()), post.title(), post.content());
        boolean postInserted = signalRepository.insertIfAbsent(postSignal);
        if (!data.isComment()) {
            spotRepository.raiseLastSignalAt(spotId, postSignal.getCreatedAt());
            return postInserted ? SignalLoadOutcome.LOADED : SignalLoadOutcome.DUPLICATE;
        }
        ChangedComment comment = data.comment();
        Signal commentSignal = Signal.ofComment(post.postId(), comment.commentId(), spotId,
                authorKey(comment.authorId()), toInstant(comment.createdAt()), comment.content());
        boolean commentInserted = signalRepository.insertIfAbsent(commentSignal);
        spotRepository.raiseLastSignalAt(spotId, commentSignal.getCreatedAt());
        return commentInserted ? SignalLoadOutcome.LOADED : SignalLoadOutcome.DUPLICATE;
    }

    private static String authorKey(Long authorId) {
        return authorId == null ? null : String.valueOf(authorId);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time.atZone(MESSAGE_ZONE).toInstant();
    }
}
