package com.ilink.test.ilinkcheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReminderConfig {

    @Bean
    public Clock reminderClock(@Value("${app.reminder.zone:Asia/Shanghai}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
