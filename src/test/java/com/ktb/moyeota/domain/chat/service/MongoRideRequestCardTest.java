package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageAppendedEvent;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@RecordApplicationEvents
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatSystemMessageService.class, MongoSystemMessageWriter.class, ChatMessageRepository.class,
        ClockConfig.class, MongoTestContainerConfig.class})
class MongoRideRequestCardTest {

    @Autowired
    private ChatSystemMessageService chatSystemMessageService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationEvents events;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        transactionTemplate = new TransactionTemplate(transactionManager);
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (String entity : new String[]{"ChatRoom", "Companion", "User"}) {
                entityManager.createQuery("delete from " + entity).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("스케줄러가 같은 동행에 운행 시작 확인 카드를 여러 번 요청해도 MongoDB 에는 한 장만 남고 한 번만 전달한다")
    void storesRideStartCardOnce() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host(), CompanionStatus.RECRUITING));

        requestRideStart(chatRoom);
        requestRideStart(chatRoom);
        requestRideStart(chatRoom);

        assertThat(cardsOf(chatRoom)).extracting(ChatMessage::getType)
                .containsExactly(MessageType.SYSTEM_RIDE_START_REQUESTED);
        assertThat(events.stream(ChatMessageAppendedEvent.class).map(ChatMessageAppendedEvent::message))
                .extracting(ChatMessageItem::type)
                .containsExactly(MessageType.SYSTEM_RIDE_START_REQUESTED);
    }

    @Test
    @DisplayName("운행 시작 · 종료 확인 카드는 서로 다른 카드로 한 장씩 남는다")
    void storesStartAndEndCardsSeparately() {
        ChatRoom chatRoom = chatRoomOf(carpool(host(), CompanionStatus.IN_PROGRESS, 2));

        requestRideStart(chatRoom);
        transactionTemplate.executeWithoutResult(status -> chatSystemMessageService.requestRideEnd(chatRoom));
        transactionTemplate.executeWithoutResult(status -> chatSystemMessageService.requestRideEnd(chatRoom));

        assertThat(cardsOf(chatRoom)).extracting(ChatMessage::getSeq, ChatMessage::getType)
                .containsExactly(
                        tuple(1L, MessageType.SYSTEM_RIDE_START_REQUESTED),
                        tuple(2L, MessageType.SYSTEM_RIDE_END_REQUESTED));
    }

    @Test
    @DisplayName("여러 서버의 스케줄러가 같은 동행에 동시에 요청해도 카드는 한 장만 남는다")
    void storesOneCardUnderConcurrentSchedulers() throws Exception {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host(), CompanionStatus.RECRUITING));
        int schedulers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(schedulers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < schedulers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                requestRideStart(chatRoom);
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

        assertThat(cardsOf(chatRoom)).hasSize(1);
    }

    private void requestRideStart(ChatRoom chatRoom) {
        transactionTemplate.executeWithoutResult(status -> chatSystemMessageService.requestRideStart(chatRoom));
    }

    private User host() {
        return transactionTemplate.execute(status -> {
            User host = user("방장");
            entityManager.persist(host);
            return host;
        });
    }

    private ChatRoom chatRoomOf(Companion companion) {
        return transactionTemplate.execute(status -> {
            entityManager.persist(companion);
            ChatRoom chatRoom = ChatRoom.create(companion);
            entityManager.persist(chatRoom);
            return chatRoom;
        });
    }

    private List<ChatMessage> cardsOf(ChatRoom chatRoom) {
        Query query = new Query(Criteria.where("room_id").is(chatRoom.getId())).with(Sort.by("seq"));
        return mongoTemplate.find(query, ChatMessage.class);
    }
}
