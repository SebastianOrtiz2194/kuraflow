package com.kuraflow.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Keeps one key per login session, {@code auth:refresh-session:<sid>}, holding the id of the only
 * refresh token currently valid for that session. The key expires with the refresh token.
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    static final String KEY_PREFIX = "auth:refresh-session:";

    // Compare-and-set in one round trip, so two concurrent refreshes can't both succeed.
    // A mismatch means reuse of an old token (or a revoked session): revoke the session.
    private static final RedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
              return 1
            end
            redis.call('DEL', KEYS[1])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public void create(String sessionId, String tokenId, Duration ttl) {
        redis.opsForValue().set(key(sessionId), tokenId, ttl);
    }

    @Override
    public boolean rotate(String sessionId, String currentTokenId, String nextTokenId, Duration ttl) {
        Long rotated = redis.execute(ROTATE, List.of(key(sessionId)),
                currentTokenId, nextTokenId, String.valueOf(ttl.toSeconds()));
        return rotated != null && rotated == 1L;
    }

    @Override
    public void revoke(String sessionId) {
        redis.delete(key(sessionId));
    }

    private static String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
