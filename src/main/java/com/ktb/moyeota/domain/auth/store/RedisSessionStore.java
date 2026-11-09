package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.auth", name = "session-store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    static final String SEQUENCE_KEY = "auth:session:seq";
    static final String SESSION_PREFIX = "auth:session:";
    static final String TOKEN_PREFIX = "auth:token:";
    static final String USER_PREFIX = "auth:user:";

    private static final String TOKENS_SUFFIX = ":tokens";
    private static final String SESSIONS_SUFFIX = ":sessions";
    private static final String SESSION_ID = "session_id";
    private static final String USER_ID = "user_id";
    private static final String ABSOLUTE_EXPIRES_AT = "absolute_expires_at";
    private static final String SUPERSEDED_AT = "superseded_at";

    private static final RedisScript<Long> CREATE = RedisScript.of("""
            redis.call('HSET', KEYS[1], 'user_id', ARGV[2], 'absolute_expires_at', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('HSET', KEYS[2], 'session_id', ARGV[1], 'user_id', ARGV[2], 'absolute_expires_at', ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[5])
            redis.call('PEXPIRE', KEYS[3], ARGV[4])
            redis.call('SADD', KEYS[4], ARGV[1])
            local remaining = redis.call('PTTL', KEYS[4])
            if remaining < tonumber(ARGV[4]) then
                redis.call('PEXPIRE', KEYS[4], ARGV[4])
            end
            return 1
            """, Long.class);

    private static final RedisScript<Long> ROTATE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            if redis.call('HSETNX', KEYS[1], 'superseded_at', ARGV[1]) == 0 then
                return 0
            end
            local fields = redis.call('HMGET', KEYS[1], 'session_id', 'user_id', 'absolute_expires_at')
            redis.call('HSET', KEYS[2], 'session_id', fields[1], 'user_id', fields[2], 'absolute_expires_at', fields[3])
            local remaining = redis.call('PTTL', KEYS[1])
            if remaining > 0 then
                redis.call('PEXPIRE', KEYS[2], remaining)
            end
            redis.call('SADD', KEYS[3], ARGV[2])
            return 1
            """, Long.class);

    private static final RedisScript<Long> DELETE_SESSION = RedisScript.of("""
            for _, token in ipairs(redis.call('SMEMBERS', KEYS[2])) do
                redis.call('DEL', ARGV[1] .. token)
            end
            local userId = redis.call('HGET', KEYS[1], 'user_id')
            if userId then
                redis.call('SREM', ARGV[2] .. userId .. ARGV[3], ARGV[4])
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final RedisScript<Long> DELETE_ALL_BY_USER = RedisScript.of("""
            for _, sessionId in ipairs(redis.call('SMEMBERS', KEYS[1])) do
                local sessionKey = ARGV[1] .. sessionId
                local tokensKey = sessionKey .. ARGV[3]
                for _, token in ipairs(redis.call('SMEMBERS', tokensKey)) do
                    redis.call('DEL', ARGV[2] .. token)
                end
                redis.call('DEL', sessionKey, tokensKey)
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Override
    public <T> T withinStoreTransaction(Supplier<T> operations) {
        return operations.get();
    }

    @Override
    public Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt) {
        Long sessionId = redisTemplate.opsForValue().increment(SEQUENCE_KEY);
        long ttlMillis = Math.max(1, Duration.between(LocalDateTime.now(clock), absoluteExpiresAt).toMillis());
        redisTemplate.execute(CREATE,
                List.of(sessionKey(sessionId), tokenKey(tokenHash), tokensKey(sessionId), userSessionsKey(userId)),
                String.valueOf(sessionId), String.valueOf(userId), absoluteExpiresAt.toString(),
                String.valueOf(ttlMillis), tokenHash);
        return sessionId;
    }

    @Override
    public Optional<SessionTokenView> findToken(String tokenHash) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(tokenKey(tokenHash));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        Object supersededAt = fields.get(SUPERSEDED_AT);
        return Optional.of(new SessionTokenView(
                tokenHash,
                Long.valueOf((String) fields.get(SESSION_ID)),
                Long.valueOf((String) fields.get(USER_ID)),
                supersededAt == null ? null : LocalDateTime.parse((String) supersededAt),
                LocalDateTime.parse((String) fields.get(ABSOLUTE_EXPIRES_AT))));
    }

    @Override
    public boolean rotate(Long sessionId, String tokenHash, String newTokenHash, LocalDateTime now) {
        Long rotated = redisTemplate.execute(ROTATE,
                List.of(tokenKey(tokenHash), tokenKey(newTokenHash), tokensKey(sessionId)),
                now.toString(), newTokenHash);
        return Long.valueOf(1).equals(rotated);
    }

    @Override
    public void deleteSession(Long sessionId) {
        redisTemplate.execute(DELETE_SESSION,
                List.of(sessionKey(sessionId), tokensKey(sessionId)),
                TOKEN_PREFIX, USER_PREFIX, SESSIONS_SUFFIX, String.valueOf(sessionId));
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        redisTemplate.execute(DELETE_ALL_BY_USER,
                List.of(userSessionsKey(userId)),
                SESSION_PREFIX, TOKEN_PREFIX, TOKENS_SUFFIX);
    }

    private static String sessionKey(Long sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private static String tokensKey(Long sessionId) {
        return SESSION_PREFIX + sessionId + TOKENS_SUFFIX;
    }

    private static String tokenKey(String tokenHash) {
        return TOKEN_PREFIX + tokenHash;
    }

    private static String userSessionsKey(Long userId) {
        return USER_PREFIX + userId + SESSIONS_SUFFIX;
    }
}
