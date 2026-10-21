package com.example.shop.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.example.shop.orders", "com.example.shop.common"})
@EntityScan({"com.example.shop.orders", "com.example.shop.common"})
@EnableJpaRepositories({"com.example.shop.orders", "com.example.shop.common"})
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
