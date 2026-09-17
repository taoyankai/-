package com.hz.delivery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 华中商贸配送中心 - 慰问品配送服务端
 */
@SpringBootApplication
@MapperScan("com.hz.delivery.mapper")
@EnableScheduling
public class HzDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(HzDeliveryApplication.class, args);
    }
}
