package com.ktb.moyeota.domain.congestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({MongoTestContainerConfig.class, CongestionJobRepository.class})
class CongestionJobRepositoryTest {

    private static final Instant WINDOW_END_0710 = Instant.parse("2026-11-01T22:10:00Z");
    private static final Instant WINDOW_END_0720 = Instant.parse("2026-11-01T22:20:00Z");
    private static final Instant WINDOW_END_0730 = Instant.parse("2026-11-01T22:30:00Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CongestionJobRepository jobRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("congestion_jobs");
    }

    @Test
    @DisplayName("기록이 없으면 마지막으로 보낸 구간 끝도 없다")
    void emptyWithoutRecord() {
        assertThat(jobRepository.findLastWindowEnd()).isEmpty();
    }

    @Test
    @DisplayName("처음 기록은 한 번만 성공한다")
    void startsOnce() {
        boolean first = jobRepository.start(WINDOW_END_0710);
        boolean second = jobRepository.start(WINDOW_END_0720);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jobRepository.findLastWindowEnd()).contains(WINDOW_END_0710);
    }

    @Test
    @DisplayName("읽은 구간 끝이 그대로일 때만 다음 구간 끝으로 옮긴다")
    void advancesOnlyFromCurrentEnd() {
        jobRepository.start(WINDOW_END_0710);

        boolean advanced = jobRepository.advance(WINDOW_END_0710, WINDOW_END_0720);
        boolean stale = jobRepository.advance(WINDOW_END_0710, WINDOW_END_0730);

        assertThat(advanced).isTrue();
        assertThat(stale).isFalse();
        assertThat(jobRepository.findLastWindowEnd()).contains(WINDOW_END_0720);
    }
}
