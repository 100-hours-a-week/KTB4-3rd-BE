package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.domain.congestion.document.Signal;
import com.ktb.moyeota.domain.congestion.message.BundleComment;
import com.ktb.moyeota.domain.congestion.message.PostBundle;
import com.ktb.moyeota.domain.congestion.repository.SignalRepository;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class PostBundleReader {

    static final int MAX_POSTS = 50;

    private static final ZoneId MESSAGE_ZONE = ZoneId.of("Asia/Seoul");

    private final SignalRepository signalRepository;
    private final AuthorKeyHasher authorKeyHasher;

    public List<PostBundle> read(Long spotId, CongestionWindow window) {
        Instant start = window.start().toInstant();
        List<Signal> signals = signalRepository.findInWindow(spotId, start, window.end().toInstant());
        Map<Long, List<Signal>> commentsByPost = signals.stream()
                .filter(Signal::isComment)
                .collect(Collectors.groupingBy(Signal::getPostId));
        List<Signal> posts = signals.stream().filter(signal -> !signal.isComment()).toList();

        Set<Long> contextPostIds = new HashSet<>(commentsByPost.keySet());
        posts.forEach(post -> contextPostIds.remove(post.getPostId()));
        List<Signal> contextPosts = contextPostIds.isEmpty() ? List.of() : signalRepository.findPosts(contextPostIds);

        return Stream.concat(posts.stream(), contextPosts.stream())
                .sorted(Comparator.comparing(Signal::getCreatedAt).reversed())
                .limit(MAX_POSTS)
                .sorted(Comparator.comparing(Signal::getCreatedAt))
                .map(post -> toBundle(post, commentsByPost.getOrDefault(post.getPostId(), List.of()), start))
                .toList();
    }

    private PostBundle toBundle(Signal post, List<Signal> comments, Instant windowStart) {
        return new PostBundle(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                authorKeyHasher.hash(post.getAuthorKey()),
                toOffset(post.getCreatedAt()),
                post.getCreatedAt().isBefore(windowStart),
                comments.stream()
                        .sorted(Comparator.comparing(Signal::getCreatedAt))
                        .map(this::toComment)
                        .toList());
    }

    private BundleComment toComment(Signal comment) {
        return new BundleComment(
                comment.getCommentId(),
                comment.getContent(),
                authorKeyHasher.hash(comment.getAuthorKey()),
                toOffset(comment.getCreatedAt()));
    }

    private static OffsetDateTime toOffset(Instant time) {
        return time.atZone(MESSAGE_ZONE).toOffsetDateTime();
    }
}
