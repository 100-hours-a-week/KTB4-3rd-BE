package com.ktb.moyeota.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@DataMongoTest(properties = "moyeota.chat.max-seq-attempts=1000")
@Import({MongoTestContainerConfig.class, ChatMessageRepository.class})
class ChatMessageRepositoryTest {

    private static final String COLLECTION = "chat_messages";
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final int MESSAGES_PER_SENDER = 10;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(COLLECTION);
        ChatMongoScripts.applyIndexes();
    }

    @Test
    @DisplayName("방마다 1부터 빈틈없이 번호를 매겨 저장한다")
    void numbersEachRoomFromOne() {
        chatMessageRepository.append(text(1L, "a"));
        chatMessageRepository.append(text(2L, "b"));
        chatMessageRepository.append(text(1L, "c"));

        ChatMessage saved = chatMessageRepository.append(text(1L, "d"));

        assertThat(saved.getSeq()).isEqualTo(3L);
        assertThat(seqsOf(1L)).containsExactly(1L, 2L, 3L);
        assertThat(seqsOf(2L)).containsExactly(1L);
    }

    @Test
    @DisplayName("같은 방에 같은 client_message_id 로 다시 저장하면 새로 저장하지 않고 처음 저장한 메시지를 돌려준다")
    void returnsFirstMessageForSameClientMessageId() {
        ChatMessage first = chatMessageRepository.append(
                ChatMessage.text(1L, 7L, "dup-1", "처음 보낸 내용", NOW));

        ChatMessage resent = chatMessageRepository.append(
                ChatMessage.text(1L, 7L, "dup-1", "다시 보낸 내용", NOW.plusSeconds(1)));

        assertThat(resent.getId()).isEqualTo(first.getId());
        assertThat(resent.getSeq()).isEqualTo(1L);
        assertThat(resent.getContent()).isEqualTo("처음 보낸 내용");
        assertThat(seqsOf(1L)).containsExactly(1L);
    }

    @Test
    @DisplayName("_id 는 UUID 표준 바이너리로 저장된다")
    void storesIdAsStandardUuid() {
        ChatMessage saved = chatMessageRepository.append(text(1L, "a"));

        BsonDocument raw = mongoTemplate.getCollection(COLLECTION).withDocumentClass(BsonDocument.class).find().first();

        assertThat(raw.getBinary("_id").getType()).isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
        assertThat(raw.getBinary("_id").asUuid()).isEqualTo(saved.getId());
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @ParameterizedTest(name = "{0}명이 동시에 보내도")
    @ValueSource(ints = {2, 4, 8, 16, 20})
    @DisplayName("같은 방에 여러 명이 동시에 보내도 번호가 겹치거나 빠지지 않는다")
    void numbersWithoutGapsUnderConcurrentSenders(int senders) throws Exception {
        runConcurrently(senders, sender -> {
            for (int i = 0; i < MESSAGES_PER_SENDER; i++) {
                chatMessageRepository.append(text(1L, sender + "-" + i));
            }
        });

        long total = (long) senders * MESSAGES_PER_SENDER;
        assertThat(seqsOf(1L)).containsExactlyElementsOf(LongStream.rangeClosed(1, total).boxed().toList());
    }

    @Test
    @DisplayName("동시에 저장되는 동안 n+1 번이 보이면 n 번도 이미 보인다")
    void neverExposesHigherSeqBeforeLower() throws Exception {
        AtomicBoolean writing = new AtomicBoolean(true);
        List<List<Long>> gaps = new CopyOnWriteArrayList<>();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<?> reading = reader.submit(() -> {
            while (writing.get()) {
                List<Long> seqs = seqsOf(1L);
                for (int i = 0; i < seqs.size(); i++) {
                    if (seqs.get(i) != i + 1) {
                        gaps.add(seqs);
                        break;
                    }
                }
            }
        });

        try {
            runConcurrently(20, sender -> {
                for (int i = 0; i < MESSAGES_PER_SENDER; i++) {
                    chatMessageRepository.append(text(1L, sender + "-" + i));
                }
            });
        } finally {
            writing.set(false);
            reading.get();
            reader.shutdown();
        }

        assertThat(gaps).isEmpty();
        assertThat(seqsOf(1L)).hasSize(20 * MESSAGES_PER_SENDER);
    }

    private ChatMessage text(Long roomId, String content) {
        return ChatMessage.text(roomId, 7L, UUID.randomUUID().toString(), content, NOW);
    }

    private List<Long> seqsOf(Long roomId) {
        Query query = new Query(Criteria.where("room_id").is(roomId)).with(Sort.by("seq"));
        return mongoTemplate.find(query, ChatMessage.class).stream().map(ChatMessage::getSeq).toList();
    }

    private static void runConcurrently(int threads, SenderTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int sender = 0; sender < threads; sender++) {
            int id = sender;
            futures.add(pool.submit(() -> {
                start.await();
                task.run(id);
                return null;
            }));
        }
        start.countDown();
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    @FunctionalInterface
    private interface SenderTask {

        void run(int sender);
    }
}
