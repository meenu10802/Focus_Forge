package com.focusforge.quest;

import com.focusforge.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class QuestEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestEngineApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // Topics this service produces. 3 partitions, keyed by userId.
    @Bean
    NewTopic taskCompletedTopic() {
        return TopicBuilder.name(Topics.TASK_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic taskAbandonedTopic() {
        return TopicBuilder.name(Topics.TASK_ABANDONED).partitions(3).replicas(1).build();
    }
}
