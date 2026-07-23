package com.ilink.test.ilinkcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IlinkCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(IlinkCheckApplication.class, args);
    }

}
