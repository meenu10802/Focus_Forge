package com.focusforge.quest;

import com.focusforge.quest.ai.AiQuestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Singleton containers shared by every integration test class in this module
 * (started once per JVM; Spring's context cache then reuses one app context).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "focus.outbox.poll-interval-ms=100"})
public abstract class AbstractIntegrationTest {

    /** Declared here (not per class) so all subclasses share one cached Spring context. */
    @MockBean
    protected AiQuestClient ai;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
