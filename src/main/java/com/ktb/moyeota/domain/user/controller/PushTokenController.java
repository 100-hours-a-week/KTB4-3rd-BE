package com.ktb.moyeota.domain.user.controller;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.service.PushTokenService;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PutMapping
    public ResponseEntity<Void> register(@AuthUser Long userId, @Valid @RequestBody PushTokenRequest request) {
        pushTokenService.register(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthUser Long userId, @Valid @RequestBody PushTokenRequest request) {
        pushTokenService.delete(userId, request);
        return ResponseEntity.noContent().build();
    }
}
