package com.ktb.moyeota.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class UserEventSenderIntegrationTest {

    private static final String DESTINATION = "/sub/settlement";
    private static final String USER_DESTINATION = "/user" + DESTINATION;
    private static final long HOST_ID = 7L;
    private static final long RIDER_ID = 8L;
    private static final String PROBE_CHANNEL = "moyeota:local:ws:v1:probe";

    record Event(String type, Long settlementId) {
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserEventSender userEventSender;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<StompSession> sessions = new ArrayList<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    @BeforeEach
    void startScheduler() throws InterruptedException {
        scheduler.initialize();
        awaitRelaySubscription();
    }

    @AfterEach
    void disconnect() {
        sessions.forEach(StompSession::disconnect);
        scheduler.shutdown();
    }

    @Test
    @DisplayName("본인 채널을 구독한 사용자에게만 이벤트가 가고, 같은 채널을 구독한 다른 사용자는 받지 않는다")
    void deliversOnlyToTargetUser() throws Exception {
        BlockingQueue<String> host = subscribe(HOST_ID, USER_DESTINATION);
        BlockingQueue<String> rider = subscribe(RIDER_ID, USER_DESTINATION);

        userEventSender.send(HOST_ID, UserChannel.SETTLEMENT, new Event("METER_ANALYSIS_FINISHED", 77L));

        assertThat(host.poll(3, TimeUnit.SECONDS))
                .isEqualTo("{\"type\":\"METER_ANALYSIS_FINISHED\",\"settlement_id\":77}");
        assertThat(rider.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("같은 사용자가 여러 곳에서 연결해 있으면 모든 연결이 받는다")
    void deliversToEverySessionOfUser() throws Exception {
        BlockingQueue<String> phone = subscribe(HOST_ID, USER_DESTINATION);
        BlockingQueue<String> laptop = subscribe(HOST_ID, USER_DESTINATION);

        userEventSender.send(HOST_ID, UserChannel.SETTLEMENT, new Event("METER_ANALYSIS_FINISHED", 77L));

        assertThat(phone.poll(3, TimeUnit.SECONDS)).contains("\"settlement_id\":77");
        assertThat(laptop.poll(3, TimeUnit.SECONDS)).contains("\"settlement_id\":77");
    }

    @Test
    @DisplayName("본인 채널이 아닌 공용 주소를 구독해도 개인 이벤트는 받지 않는다")
    void doesNotLeakToSharedDestination() throws Exception {
        BlockingQueue<String> host = subscribe(HOST_ID, USER_DESTINATION);
        BlockingQueue<String> eavesdropper = new LinkedBlockingQueue<>();
        connect(RIDER_ID).subscribe(DESTINATION, collector(eavesdropper));

        userEventSender.send(HOST_ID, UserChannel.SETTLEMENT, new Event("METER_ANALYSIS_FINISHED", 77L));

        assertThat(host.poll(3, TimeUnit.SECONDS)).isNotNull();
        assertThat(eavesdropper.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("다른 사람의 개인 채널 내부 주소를 직접 구독해도 받지 못한다")
    void blocksDirectSubscriptionToOthersSessionDestination() throws Exception {
        BlockingQueue<String> host = subscribe(HOST_ID, USER_DESTINATION);
        String hostSessionId = simpUserRegistry.getUser(String.valueOf(HOST_ID)).getSessions().iterator().next().getId();
        BlockingQueue<String> eavesdropper = new LinkedBlockingQueue<>();
        connect(RIDER_ID).subscribe(DESTINATION + "-user" + hostSessionId, collector(eavesdropper));

        userEventSender.send(HOST_ID, UserChannel.SETTLEMENT, new Event("METER_ANALYSIS_FINISHED", 77L));

        assertThat(host.poll(3, TimeUnit.SECONDS)).isNotNull();
        assertThat(eavesdropper.poll(500, TimeUnit.MILLISECONDS)).isNull();
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

    private BlockingQueue<String> subscribe(long userId, String destination) throws Exception {
        StompSession session = connect(userId);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        int before = subscriptionCount(userId, destination);
        session.subscribe(destination, collector(received));
        awaitSubscriptionCount(userId, destination, before + 1);
        return received;
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

    private void awaitSubscriptionCount(long userId, String destination, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (subscriptionCount(userId, destination) < expected) {
            assertThat(System.nanoTime()).as("구독 등록 대기 시간 초과").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private int subscriptionCount(long userId, String destination) {
        return simpUserRegistry.findSubscriptions(subscription ->
                subscription.getDestination().equals(destination)
                        && subscription.getSession().getUser().getName().equals(String.valueOf(userId))).size();
    }
}
