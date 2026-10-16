package com.ktb.moyeota.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerRoleLogger {

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void logRole() {
        log.info("[SERVER_ROLE] 서버 역할: {}", ServerRole.from(environment.getProperty(ServerRole.ENV_NAME)));
    }
}
