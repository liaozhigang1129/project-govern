package com.company.zhiyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan("com.company.zhiyu")
public class ZhiyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiyuApplication.class, args);
    }
}