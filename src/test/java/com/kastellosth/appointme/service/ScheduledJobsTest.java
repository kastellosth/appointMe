package com.kastellosth.appointme.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledJobsTest {

    @Mock
    private ReminderRunner reminders;

    @Mock
    private IdempotencyService idempotency;

    @InjectMocks
    private ScheduledJobs jobs;

    @Test
    void keepsDeletingUntilABatchIsNotFull() {
        when(idempotency.deleteOlderThan(any(), anyInt())).thenReturn(1000, 1000, 3);

        jobs.deleteOldIdempotencyKeys();

        verify(idempotency, times(3)).deleteOlderThan(any(), anyInt());
    }

    @Test
    void anExactlyFullLastBatchNeedsOneMoreEmptyCall() {
        when(idempotency.deleteOlderThan(any(), anyInt())).thenReturn(1000, 0);

        jobs.deleteOldIdempotencyKeys();

        verify(idempotency, times(2)).deleteOlderThan(any(), anyInt());
    }

    @Test
    void keepsKeysForSevenDays() {
        when(idempotency.deleteOlderThan(any(), anyInt())).thenReturn(0);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        Duration week = Duration.ofDays(7);

        Instant earliest = Instant.now().minus(week);
        jobs.deleteOldIdempotencyKeys();
        Instant latest = Instant.now().minus(week);

        verify(idempotency).deleteOlderThan(cutoff.capture(), anyInt());
        assertFalse(cutoff.getValue().isBefore(earliest));
        assertFalse(cutoff.getValue().isAfter(latest));
    }
}
