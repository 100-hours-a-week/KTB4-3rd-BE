package com.ktb.moyeota.domain.community.service;

import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.event.CommunityCommentCreatedEvent;
import com.ktb.moyeota.domain.community.event.CommunityPostCreatedEvent;
import com.ktb.moyeota.domain.community.message.CommunityPostMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({CommunityPostOutboxListener.class, OutboxRecorder.class, EventEnvelopeFactory.class,
        CommunityPostOutboxListenerTest.ClockConfig.class})
class CommunityPostOutboxListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("게시글을 쓰면 게시글 번호 키로 원글만 담아 community.post.created 를 기록한다")
    void recordsPostCreated() {
        User writer = entityManager.persist(user("작성자"));
        CommunityPost post = persistPost(writer);

        eventPublisher.publishEvent(new CommunityPostCreatedEvent(post.getId()));

        OutboxEvent event = single(outbox());
        assertThat(event.getTopic()).isEqualTo(CommunityPostMessages.CHANGED_TOPIC);
        assertThat(event.getMessageKey()).isEqualTo(String.valueOf(post.getId()));
        assertThat(event.getEventType()).isEqualTo(CommunityPostMessages.POST_CREATED);
        JsonNode data = payload(event);
        assertPost(data.get("post"), post, writer);
        assertThat(data.get("comment").isNull()).isTrue();
    }

    @Test
    @DisplayName("댓글을 쓰면 원글 번호 키로 원글과 댓글을 함께 담아 community.comment.created 를 기록한다")
    void recordsCommentCreated() {
        User writer = entityManager.persist(user("작성자"));
        User commenter = entityManager.persist(user("댓글러"));
        CommunityPost post = persistPost(writer);
        CommunityComment comment = entityManager.persist(CommunityComment.create(post, commenter, "저도 기다리는 중"));

        eventPublisher.publishEvent(new CommunityCommentCreatedEvent(comment.getId()));

        OutboxEvent event = single(outbox());
        assertThat(event.getMessageKey()).isEqualTo(String.valueOf(post.getId()));
        assertThat(event.getEventType()).isEqualTo(CommunityPostMessages.COMMENT_CREATED);
        JsonNode data = payload(event);
        assertPost(data.get("post"), post, writer);
        JsonNode commentData = data.get("comment");
        assertThat(commentData.get("comment_id").asLong()).isEqualTo(comment.getId());
        assertThat(commentData.get("author_id").asLong()).isEqualTo(commenter.getId());
        assertThat(commentData.get("content").asString()).isEqualTo("저도 기다리는 중");
        assertThat(commentData.get("created_at").isNull()).isFalse();
    }

    private CommunityPost persistPost(User writer) {
        return entityManager.persist(CommunityPost.create(
                writer, "판교역 혼잡", "출구 사람 많아요", new BigDecimal("37.394800"), new BigDecimal("127.111200")));
    }

    private static void assertPost(JsonNode post, CommunityPost expected, User writer) {
        assertThat(post.get("post_id").asLong()).isEqualTo(expected.getId());
        assertThat(post.get("author_id").asLong()).isEqualTo(writer.getId());
        assertThat(post.get("title").asString()).isEqualTo("판교역 혼잡");
        assertThat(post.get("content").asString()).isEqualTo("출구 사람 많아요");
        assertThat(post.get("lat").decimalValue()).isEqualByComparingTo("37.3948");
        assertThat(post.get("lng").decimalValue()).isEqualByComparingTo("127.1112");
        assertThat(post.get("created_at").isNull()).isFalse();
    }

    private List<OutboxEvent> outbox() {
        entityManager.flush();
        return outboxEventRepository.findAll();
    }

    private static OutboxEvent single(List<OutboxEvent> events) {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private JsonNode payload(OutboxEvent event) {
        return jsonMapper.readTree(event.getPayload());
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }
}
