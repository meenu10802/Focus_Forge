package com.focusforge.quest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QuestEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestEngineApplication.class, args);
    }
}
