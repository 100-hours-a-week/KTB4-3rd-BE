package com.ktb.moyeota.domain.congestion.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import(MongoTestContainerConfig.class)
class SignalTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-02T01:07:00Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("signals");
        CongestionMongoScripts.applyIndexes();
    }

    @Test
    @DisplayName("원글과 그 댓글은 같은 게시글이어도 각각 들어간다")
    void savesPostAndComment() {
        mongoTemplate.insert(Signal.ofPost(1L, 101L, "author-a", WRITTEN_AT, "판교역 혼잡", "출구 사람 많아요"));
        mongoTemplate.insert(Signal.ofComment(1L, 7L, 101L, "author-b", WRITTEN_AT, "저도 기다리는 중"));

        assertThat(mongoTemplate.findAll(Signal.class))
                .extracting(Signal::getPostId, Signal::getCommentId, Signal::isComment)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(1L, null, false),
                        Tuple.tuple(1L, 7L, true));
    }

    @Test
    @DisplayName("제목은 30자, 본문은 280자까지만 남긴다")
    void truncatesTitleAndContent() {
        Signal signal = Signal.ofPost(1L, 101L, "author-a", WRITTEN_AT, "가".repeat(40), "나".repeat(500));

        assertThat(signal.getTitle()).hasSize(Signal.TITLE_MAX_LENGTH);
        assertThat(signal.getContent()).hasSize(Signal.CONTENT_MAX_LENGTH);
    }

    @Test
    @DisplayName("같은 원글 사본은 두 번 들어가지 않는다")
    void rejectsDuplicatePost() {
        mongoTemplate.insert(Signal.ofPost(1L, 101L, "author-a", WRITTEN_AT, "판교역 혼잡", "출구 사람 많아요"));

        assertThatThrownBy(() -> mongoTemplate.insert(
                Signal.ofPost(1L, 101L, "author-a", WRITTEN_AT, "판교역 혼잡", "출구 사람 많아요")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("같은 댓글 사본은 두 번 들어가지 않는다")
    void rejectsDuplicateComment() {
        mongoTemplate.insert(Signal.ofComment(1L, 7L, 101L, "author-b", WRITTEN_AT, "저도 기다리는 중"));

        assertThatThrownBy(() -> mongoTemplate.insert(
                Signal.ofComment(1L, 7L, 101L, "author-b", WRITTEN_AT, "저도 기다리는 중")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
