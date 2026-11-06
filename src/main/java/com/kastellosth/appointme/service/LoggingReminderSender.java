package com.kastellosth.appointme.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingReminderSender implements ReminderSender {

    @Override
    public void send(ReminderMessage message) {
        log.info("Reminder {} would be sent now", message.reminderId());
    }
}
