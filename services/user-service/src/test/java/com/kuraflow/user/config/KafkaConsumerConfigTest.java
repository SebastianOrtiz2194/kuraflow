package com.kuraflow.user.config;

import com.kuraflow.shared.event.BadgeEarnedEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the consumer settings in application.yml, not a copy of them. */
class KafkaConsumerConfigTest {

    @Test
    void deserializesTheEventsThisServiceConsumes() {
        Object event = deserialize("com.kuraflow.shared.event.BadgeEarnedEvent", "{\"badgeCode\":\"STREAK_3\"}");

        assertThat(event).isInstanceOf(BadgeEarnedEvent.class);
    }

    @Test
    void refusesTypeHeadersOutsideTheEventPackages() {
        // A forged __TypeId__ must not make the consumer instantiate arbitrary classes, such as our own entities.
        assertThatThrownBy(() -> deserialize("com.kuraflow.user.entity.User", "{}"))
                .hasMessageContaining("not in the trusted packages");
    }

    private static Object deserialize(String typeHeader, String json) {
        try (JsonDeserializer<Object> deserializer = new JsonDeserializer<>()) {
            deserializer.configure(consumerProperties(), false);
            RecordHeaders headers = new RecordHeaders();
            headers.add(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, typeHeader.getBytes(StandardCharsets.UTF_8));
            return deserializer.deserialize("badge.earned", headers, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, Object> consumerProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = Objects.requireNonNull(yaml.getObject());
        String prefix = "spring.kafka.consumer.properties.";
        Map<String, Object> config = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                config.put(key.substring(prefix.length()), properties.getProperty(key));
            }
        }
        return config;
    }
}
