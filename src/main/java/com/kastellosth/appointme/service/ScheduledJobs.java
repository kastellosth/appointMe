package com.kastellosth.appointme.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class ScheduledJobs {

    private static final Duration KEY_RETENTION = Duration.ofDays(7);
    private static final int KEY_BATCH_SIZE = 1000;

    private final ReminderRunner reminders;
    private final IdempotencyService idempotency;

    public ScheduledJobs(ReminderRunner reminders, IdempotencyService idempotency) {
        this.reminders = reminders;
        this.idempotency = idempotency;
    }

    @Scheduled(fixedDelayString = "${app.reminders.delay:PT1M}")
    public void sendReminders() {
        reminders.runOnce(Instant.now());
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 15 3 * * *}", zone = "UTC")
    public void deleteOldIdempotencyKeys() {
        Instant cutoff = Instant.now().minus(KEY_RETENTION);
        int total = 0;
        int deleted;
        do {
            deleted = idempotency.deleteOlderThan(cutoff, KEY_BATCH_SIZE);
            total += deleted;
        } while (deleted == KEY_BATCH_SIZE);
        log.info("Deleted {} old idempotency keys", total);
    }
}
