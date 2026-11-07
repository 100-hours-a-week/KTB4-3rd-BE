package com.ktb.moyeota.domain.congestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.congestion.document.Signal;
import com.ktb.moyeota.domain.congestion.message.BundleComment;
import com.ktb.moyeota.domain.congestion.message.PostBundle;
import com.ktb.moyeota.domain.congestion.repository.SignalRepository;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({MongoTestContainerConfig.class, SignalRepository.class})
class PostBundleReaderTest {

    private static final Long PANGYO = 101L;
    private static final Long JEONGJA = 102L;
    private static final CongestionWindow WINDOW = new CongestionWindow(
            OffsetDateTime.parse("2026-11-02T07:00:00+09:00"), OffsetDateTime.parse("2026-11-02T07:10:00+09:00"));

    private final AuthorKeyHasher hasher = new AuthorKeyHasher("dGVzdC1vbmx5LWNvbmdlc3Rpb24tYXV0aG9yLWtleSE=");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SignalRepository signalRepository;

    private PostBundleReader reader;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("signals");
        CongestionMongoScripts.applyIndexes();
        reader = new PostBundleReader(signalRepository, hasher);
    }

    @Test
    @DisplayName("구간 안 원글과 댓글을 원글별로 묶고 작성자는 가명 키로 바꾼다")
    void bundlesPostsWithComments() {
        insert(post(1L, PANGYO, "07:02:00"));
        insert(comment(1L, 11L, PANGYO, "07:04:00"));
        insert(comment(1L, 12L, PANGYO, "07:03:00"));

        List<PostBundle> bundles = reader.read(PANGYO, WINDOW);

        assertThat(bundles).singleElement().satisfies(bundle -> {
            assertThat(bundle.postId()).isEqualTo(1L);
            assertThat(bundle.contextOnly()).isFalse();
            assertThat(bundle.authorKey()).isEqualTo(hasher.hash("1"));
            assertThat(bundle.createdAt()).isEqualTo(OffsetDateTime.parse("2026-11-02T07:02:00+09:00"));
            assertThat(bundle.comments()).extracting(BundleComment::commentId, BundleComment::authorKey)
                    .containsExactly(tuple(12L, hasher.hash("12")), tuple(11L, hasher.hash("11")));
        });
    }

    @Test
    @DisplayName("구간보다 오래된 원글에 구간 안 댓글이 달리면 원글을 문맥용으로 담는다")
    void oldPostIsContextOnly() {
        insert(post(2L, PANGYO, "06:30:00"));
        insert(comment(2L, 21L, PANGYO, "07:05:00"));

        List<PostBundle> bundles = reader.read(PANGYO, WINDOW);

        assertThat(bundles).singleElement().satisfies(bundle -> {
            assertThat(bundle.postId()).isEqualTo(2L);
            assertThat(bundle.contextOnly()).isTrue();
            assertThat(bundle.comments()).extracting(BundleComment::commentId).containsExactly(21L);
        });
    }

    @Test
    @DisplayName("구간 밖 글과 다른 스팟 글은 담지 않는다")
    void skipsOtherWindowsAndSpots() {
        insert(post(3L, PANGYO, "06:59:59"));
        insert(post(4L, PANGYO, "07:10:00"));
        insert(post(5L, JEONGJA, "07:05:00"));

        assertThat(reader.read(PANGYO, WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("원글이 50개를 넘으면 최근 원글 50개만 작성 순으로 담는다")
    void keepsLatestFiftyPosts() {
        LongStream.rangeClosed(1, 55).forEach(id ->
                insert(post(id, PANGYO, Instant.parse("2026-11-01T22:00:00Z").plusSeconds(id))));

        List<PostBundle> bundles = reader.read(PANGYO, WINDOW);

        assertThat(bundles).hasSize(50);
        assertThat(bundles.getFirst().postId()).isEqualTo(6L);
        assertThat(bundles.getLast().postId()).isEqualTo(55L);
    }

    private void insert(Signal signal) {
        signalRepository.insertIfAbsent(signal);
    }

    private static Signal post(Long postId, Long spotId, String time) {
        return post(postId, spotId, at(time));
    }

    private static Signal post(Long postId, Long spotId, Instant createdAt) {
        return Signal.ofPost(postId, spotId, String.valueOf(postId), createdAt, "판교역 혼잡", "사람 많아요");
    }

    private static Signal comment(Long postId, Long commentId, Long spotId, String time) {
        return Signal.ofComment(postId, commentId, spotId, String.valueOf(commentId), at(time), "저도 기다려요");
    }

    private static Instant at(String time) {
        return OffsetDateTime.parse("2026-11-02T" + time + "+09:00").toInstant();
    }
}
