package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WS 명세서(chat-websocket-api-spec_1.xlsx)의 CONNECT /ws, SUBSCRIBE /sub/chat/{room_id},
 * SEND /pub/chat/{room_id} 세 엔드포인트를 위한 기반 설정. 실제 SEND 처리 로직
 * (ChatStompController - 이번 작업 범위 아님, 아직 만들지 않았음)은 여기 없고, 이 클래스는
 * "그 로직이 동작할 수 있게 하는 배선"만 담당한다.
 *
 * 코드 설명 (설정 파일 자체가 처음이라면):
 * - @EnableWebSocketMessageBroker: 스프링에게 "STOMP를 얹은 WebSocket 메시징을 쓰겠다"고 알리는
 *   스위치. 이게 있어야 @MessageMapping, SimpMessagingTemplate 같은 것들이 동작한다.
 * - registerStompEndpoints: 클라이언트가 최초에 WebSocket 연결을 맺는 URL을 등록하는 곳.
 *   REST의 @RequestMapping 경로 등록과 비슷한 역할이라고 생각하면 된다.
 * - configureMessageBroker: 메시지가 어떤 경로 규칙으로 오가는지 정하는 곳.
 *   setApplicationDestinationPrefixes("/pub"): 클라이언트가 "/pub/..."로 보낸 메시지는
 *     서버의 @MessageMapping 메서드로 라우팅된다(REST로 치면 컨트롤러로 들어오는 요청).
 *   enableSimpleBroker("/sub"): "/sub/..."를 구독 중인 클라이언트들에게 메시지를 뿌려주는
 *     아주 단순한(인메모리) 브로커를 켠다. 지금 규모에서는 충분하지만, 서버를 여러 대로 늘리면
 *     (수평 확장) 이 인메모리 브로커는 서버 인스턴스 간에 메시지를 공유하지 못해서 RabbitMQ 같은
 *     외부 브로커로 바꿔야 한다 - 지금 당장 할 일은 아니고 알아두면 좋은 점만 적어둠.
 * - configureClientInboundChannel: 클라이언트 → 서버로 들어오는 모든 STOMP 프레임이 거쳐가는
 *   통로에 인터셉터(StompAuthChannelInterceptor)를 끼워넣는 곳. CONNECT 인증이 여기서 걸린다.
 *
 * 아직 정하지 못해서 비워둔 것 (WS 명세서상 [미정]/[v2 예정]):
 * - setAllowedOriginPatterns("*")로 일단 전체 허용해뒀는데, CorsConfig처럼 실제 허용 Origin
 *   목록으로 좁혀야 한다. 프론트 도메인이 정해지면 확인 필요.
 * - ws/wss 순수 프로토콜 vs SockJS 여부가 [미정]이라, 일단 SockJS 없이 순수 STOMP-over-WebSocket
 *   엔드포인트로만 등록해뒀다(addEndpoint에 withSockJS() 체이닝을 안 함). SockJS로 가기로 하면
 *   registerStompEndpoints를 수정해야 한다.
 * - heartbeat/재연결 정책은 [v2 예정]이라 손대지 않았다.
 *
 * 아직 이 설정만으로는 동작하지 않는 부분 (주의할 내용 - 확인 후 진행 필요):
 * - SecurityConfig의 시큐리티 필터체인이 지금 `anyRequest().hasAuthority(Authority.USER_NAME)`로
 *   모든 요청에 Bearer 토큰을 요구하고 있다. "/ws" 핸드셰이크는 (인터셉터 설명에 적었듯) HTTP 헤더로
 *   토큰을 못 실어보내기 때문에, 이대로면 핸드셰이크 단계에서부터 401로 막힌다.
 *   "/ws" 경로를 SecurityConfig에서 permitAll로 열어주고, 인증은 이 인터셉터(STOMP CONNECT 레벨)에
 *   맡기는 방식으로 가야 하는데, SecurityConfig는 Chat 도메인 코드가 아니라 전체 도메인이 같이 쓰는
 *   공용 설정이라 이번에 직접 수정하지 않았다. 수정이 필요하면 말씀해주세요 - 정확히는
 *   `.requestMatchers("/ws/**").permitAll()`을 anyRequest() 규칙보다 앞에 추가하면 될 것으로 보인다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws";
    private static final String APPLICATION_DESTINATION_PREFIX = "/pub";
    private static final String BROKER_PREFIX = "/sub";

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns("*"); // TODO: 실제 프론트 Origin으로 좁혀야 함 - 확인 필요
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROKER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
