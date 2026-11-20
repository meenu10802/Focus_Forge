package com.focusforge.identity.progress;

import com.focusforge.events.TaskAbandonedEvent;
import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

/**
 * Optimistic-lock conflicts with a concurrent dashboard read (lazy HP refill)
 * surface as exceptions here; the shared DefaultErrorHandler retries the whole
 * listener, and the rolled-back processed_events row lets the retry proceed.
 */
@Component
public class ProgressListeners {

    private final ProgressService progress;

    public ProgressListeners(ProgressService progress) {
        this.progress = progress;
    }

    @KafkaListener(topics = Topics.TASK_COMPLETED)
    public void onCompleted(TaskCompletedEvent event) {
        progress.onTaskCompleted(event);
    }

    @KafkaListener(topics = Topics.TASK_ABANDONED)
    public void onAbandoned(TaskAbandonedEvent event) {
        progress.onTaskAbandoned(event);
    }

    @Configuration
    static class TopicsConfig {
        @Bean
        NewTopic levelUpTopic() {
            return TopicBuilder.name(Topics.LEVEL_UP).partitions(3).replicas(1).build();
        }
    }
}
