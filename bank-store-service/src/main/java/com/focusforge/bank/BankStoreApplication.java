package com.focusforge.bank;

import com.focusforge.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class BankStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankStoreApplication.class, args);
    }

    @Bean
    NewTopic rewardRedeemedTopic() {
        return TopicBuilder.name(Topics.REWARD_REDEEMED).partitions(3).replicas(1).build();
    }
}
