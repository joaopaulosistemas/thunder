package com.example.shop.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.example.shop.catalog", "com.example.shop.common"})
@EntityScan({"com.example.shop.catalog", "com.example.shop.common"})
@EnableJpaRepositories({"com.example.shop.catalog", "com.example.shop.common"})
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
