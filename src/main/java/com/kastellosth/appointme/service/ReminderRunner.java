package com.kastellosth.appointme.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Deliberately not @Transactional: no database connection or lock is held while sending. */
@Slf4j
@Component
public class ReminderRunner {

    private static final int BATCH_SIZE = 50;

    private final ReminderService reminders;
    private final ReminderSender sender;

    public ReminderRunner(ReminderService reminders, ReminderSender sender) {
        this.reminders = reminders;
        this.sender = sender;
    }

    public void runOnce(Instant now) {
        reminders.createDue(now);
        for (Long id : reminders.claim(now, BATCH_SIZE)) {
            reminders.prepare(id, now).ifPresent(message -> send(id, message, now));
        }
    }

    private void send(Long id, ReminderMessage message, Instant now) {
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            log.warn("Reminder {} failed: {}", id, e.getClass().getSimpleName());
            reminders.markFailed(id, e.getClass().getSimpleName(), now);
            return;
        }
        reminders.markSent(id, now);
    }
}
