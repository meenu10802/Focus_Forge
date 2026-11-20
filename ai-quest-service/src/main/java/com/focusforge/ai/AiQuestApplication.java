package com.focusforge.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiQuestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiQuestApplication.class, args);
    }
}
