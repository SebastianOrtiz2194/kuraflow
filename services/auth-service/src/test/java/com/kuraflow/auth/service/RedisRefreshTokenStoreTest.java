package com.kuraflow.auth.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the rotation script against a real Redis: the compare-and-set lives in Lua, so mocks can't
 * cover it.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisRefreshTokenStoreTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisRefreshTokenStore store;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        store = new RedisRefreshTokenStore(redis);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void rotatesTheCurrentToken() {
        store.create("s1", "t1", TTL);

        assertThat(store.rotate("s1", "t1", "t2", TTL)).isTrue();
        assertThat(store.rotate("s1", "t2", "t3", TTL)).isTrue();
        assertThat(redis.getExpire(RedisRefreshTokenStore.KEY_PREFIX + "s1")).isPositive();
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() {
        store.create("s2", "t1", TTL);
        assertThat(store.rotate("s2", "t1", "t2", TTL)).isTrue();

        // An attacker replays t1: rejected, and the legitimate holder of t2 is logged out too.
        assertThat(store.rotate("s2", "t1", "t3", TTL)).isFalse();
        assertThat(store.rotate("s2", "t2", "t4", TTL)).isFalse();
    }

    @Test
    void revokedOrUnknownSessionsCannotRotate() {
        store.create("s3", "t1", TTL);
        store.revoke("s3");

        assertThat(store.rotate("s3", "t1", "t2", TTL)).isFalse();
        assertThat(store.rotate("never-created", "t1", "t2", TTL)).isFalse();
    }
}
