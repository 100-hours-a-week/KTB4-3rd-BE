package com.ktb.moyeota.domain.chat;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageBroadcaster;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class ChatRoomRelayIntegrationTest {

    private static final String PROBE_CHANNEL = "moyeota:local:ws:v1:probe";

    @LocalServerPort
    private int port;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatMessageBroadcaster chatMessageBroadcaster;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<StompSession> sessions = new ArrayList<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    private Long hostId;
    private Long riderId;
    private Long outsiderId;
    private Long roomId;
    private Long otherRoomId;

    @BeforeEach
    void setUp() throws InterruptedException {
        scheduler.initialize();
        transactionTemplate.executeWithoutResult(status -> {
            User host = persist(user("방장"));
            User rider = persist(user("동승자"));
            User outsider = persist(user("다른방사람"));
            Companion pot = persist(taxiPot(host, CompanionStatus.RECRUITING, 2));
            Companion otherPot = persist(taxiPot(outsider, CompanionStatus.RECRUITING, 1));
            roomId = persist(ChatRoom.create(pot)).getId();
            otherRoomId = persist(ChatRoom.create(otherPot)).getId();
            persist(participant(pot, host, PENDING));
            persist(participant(pot, rider, PENDING));
            persist(participant(otherPot, outsider, PENDING));
            hostId = host.getId();
            riderId = rider.getId();
            outsiderId = outsider.getId();
        });
        awaitRelaySubscription();
    }

    @AfterEach
    void cleanUp() {
        sessions.forEach(StompSession::disconnect);
        scheduler.shutdown();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            List<?> tables = entityManager.createNativeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'").getResultList();
            tables.forEach(table -> entityManager.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate());
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        });
    }

    @Test
    @DisplayName("사용자가 보낸 채팅은 Redis를 거쳐 보낸 사람을 포함한 방 사람 모두에게 가고, 다른 방 사람은 받지 않는다")
    void deliversUserChatToEveryoneInRoom() throws Exception {
        StompSession hostSession = connect(hostId);
        BlockingQueue<String> host = subscribe(hostSession, hostId, roomDestination(roomId));
        BlockingQueue<String> rider = subscribe(connect(riderId), riderId, roomDestination(roomId));
        BlockingQueue<String> outsider = subscribe(connect(outsiderId), outsiderId, roomDestination(otherRoomId));

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/pub/chat/" + roomId);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        hostSession.send(headers, "{\"client_message_id\":\"c-1\",\"content\":\"출발지 앞이에요\"}"
                .getBytes(StandardCharsets.UTF_8));

        String received = host.poll(3, TimeUnit.SECONDS);
        assertThat(received).contains("\"type\":\"TEXT\"", "\"content\":\"출발지 앞이에요\"");
        assertThat(rider.poll(3, TimeUnit.SECONDS)).isEqualTo(received);
        assertThat(outsider.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("시스템 메시지는 Redis를 거쳐 방 사람 모두에게 같은 JSON으로 가고, 다른 방 사람은 받지 않는다")
    void deliversSystemMessageToEveryoneInRoom() throws Exception {
        BlockingQueue<String> host = subscribe(connect(hostId), hostId, roomDestination(roomId));
        BlockingQueue<String> rider = subscribe(connect(riderId), riderId, roomDestination(roomId));
        BlockingQueue<String> outsider = subscribe(connect(outsiderId), outsiderId, roomDestination(otherRoomId));
        MessageItem join = new MessageItem(900L, MessageType.SYSTEM_JOIN, null,
                new MessageItem.SystemActor(7L, "홍길동"), null, null, LocalDateTime.of(2026, 9, 5, 8, 0));

        chatMessageBroadcaster.broadcast(new ChatMessageCreatedEvent(roomId, join));

        String expected = "{\"id\":900,\"type\":\"SYSTEM_JOIN\",\"joiner\":{\"id\":7,\"name\":\"홍길동\"},"
                + "\"created_at\":\"2026-09-05T08:00:00\"}";
        assertThat(host.poll(3, TimeUnit.SECONDS)).isEqualTo(expected);
        assertThat(rider.poll(3, TimeUnit.SECONDS)).isEqualTo(expected);
        assertThat(outsider.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private static String roomDestination(Long chatRoomId) {
        return "/sub/chat/" + chatRoomId;
    }

    private void awaitRelaySubscription() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long receivers = redisTemplate.convertAndSend(PROBE_CHANNEL, "probe");
            if (receivers != null && receivers > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Redis 구독이 준비되지 않았습니다.");
    }

    private StompSession connect(long userId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new SimpleMessageConverter());
        client.setTaskScheduler(scheduler);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessTokenProvider.issue(userId).value());
        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/api/wss", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(3, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<String> subscribe(StompSession session, long userId, String destination)
            throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, collector(received));
        awaitSubscription(userId, destination);
        return received;
    }

    private static StompFrameHandler collector(BlockingQueue<String> received) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        };
    }

    private void awaitSubscription(long userId, String destination) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (simpUserRegistry.findSubscriptions(subscription ->
                subscription.getDestination().equals(destination)
                        && subscription.getSession().getUser().getName().equals(String.valueOf(userId))).isEmpty()) {
            assertThat(System.nanoTime()).as("구독 등록 대기 시간 초과").isLessThan(deadline);
            Thread.sleep(20);
        }
    }
}
