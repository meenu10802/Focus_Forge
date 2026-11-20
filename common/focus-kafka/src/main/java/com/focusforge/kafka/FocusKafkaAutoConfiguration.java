package com.focusforge.kafka;

import com.focusforge.events.Topics;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumer error policy shared by every FocusForge Kafka consumer:
 * <ol>
 *   <li>Retry in-process 3 times, 500 ms apart (transient DB/lock hiccups).</li>
 *   <li>Then publish the record to {@code <topic>.DLT} and move on, so one
 *       poison message can never block a partition.</li>
 * </ol>
 * Deserialization failures (wrapped by {@code ErrorHandlingDeserializer})
 * skip the retries and go straight to the DLT as raw bytes.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class FocusKafkaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FocusKafkaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler focusKafkaErrorHandler(KafkaProperties props, ObjectProvider<SslBundles> ssl) {
        Map<String, Object> producerProps = props.buildProducerProperties(ssl.getIfAvailable());

        // Poison bytes (failed deserialization) need a byte[] serializer; normal failures carry the event object.
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new ByteArraySerializer())));
        templates.put(Object.class, new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new JsonSerializer<>())));

        var recoverer = new DeadLetterPublishingRecoverer(templates,
                // partition -1: let Kafka choose; the DLT needn't mirror the source topic's partition count
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, -1));

        var handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Sending {}-{}@{} to DLT after retries: {}", record.topic(), record.partition(),
                    record.offset(), ex.getMessage());
            recoverer.accept(record, ex);
        }, new FixedBackOff(500L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
