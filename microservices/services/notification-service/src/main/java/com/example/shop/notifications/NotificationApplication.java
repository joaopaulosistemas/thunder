package com.example.shop.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.example.shop.notifications", "com.example.shop.common"})
@EntityScan({"com.example.shop.notifications", "com.example.shop.common"})
@EnableJpaRepositories({"com.example.shop.notifications", "com.example.shop.common"})
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
