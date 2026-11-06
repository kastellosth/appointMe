package com.kastellosth.appointme.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderRunnerTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");

    @Mock
    private ReminderService reminders;

    @Mock
    private ReminderSender sender;

    @InjectMocks
    private ReminderRunner runner;

    private static ReminderMessage message(long id) {
        return new ReminderMessage(id, "customer@test.com", "Test Barber", "Haircut", "2026-07-02 10:00");
    }

    private void claimed(Long... ids) {
        when(reminders.claim(NOW, 50)).thenReturn(List.of(ids));
    }

    @Test
    void createsDueRemindersBeforeClaiming() {
        claimed();

        runner.runOnce(NOW);

        InOrder order = inOrder(reminders);
        order.verify(reminders).createDue(NOW);
        order.verify(reminders).claim(NOW, 50);
        verifyNoInteractions(sender);
    }

    @Test
    void marksSentOnlyAfterTheSendReturned() {
        claimed(1L);
        when(reminders.prepare(1L, NOW)).thenReturn(Optional.of(message(1)));

        runner.runOnce(NOW);

        InOrder order = inOrder(sender, reminders);
        order.verify(sender).send(message(1));
        order.verify(reminders).markSent(1L, NOW);
        verify(reminders, never()).markFailed(any(), any(), any());
    }

    @Test
    void failedSendStoresTheClassNameNeverTheMessage() {
        claimed(1L);
        when(reminders.prepare(1L, NOW)).thenReturn(Optional.of(message(1)));
        doThrow(new IllegalStateException("bounced: customer@test.com")).when(sender).send(any());

        runner.runOnce(NOW);

        verify(reminders).markFailed(1L, "IllegalStateException", NOW);
        verify(reminders, never()).markSent(any(), any());
    }

    @Test
    void nothingToSendMeansNoSendAndNoStatusChange() {
        claimed(1L);
        when(reminders.prepare(1L, NOW)).thenReturn(Optional.empty());

        runner.runOnce(NOW);

        verifyNoInteractions(sender);
        verify(reminders, never()).markSent(any(), any());
        verify(reminders, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void oneFailedSendDoesNotStopTheBatch() {
        claimed(1L, 2L);
        when(reminders.prepare(1L, NOW)).thenReturn(Optional.of(message(1)));
        when(reminders.prepare(2L, NOW)).thenReturn(Optional.of(message(2)));
        doThrow(new IllegalStateException("down")).when(sender).send(message(1));

        runner.runOnce(NOW);

        verify(reminders).markFailed(eq(1L), anyString(), eq(NOW));
        verify(reminders).markSent(2L, NOW);
    }

    @Test
    void oneReminderThatCannotBePreparedDoesNotStopTheBatch() {
        claimed(1L, 2L);
        when(reminders.prepare(1L, NOW)).thenThrow(new IllegalStateException("Reminder 1 has no appointment"));
        when(reminders.prepare(2L, NOW)).thenReturn(Optional.of(message(2)));

        runner.runOnce(NOW);

        verify(sender).send(message(2));
        verify(reminders).markSent(2L, NOW);
    }

    @Test
    void databaseErrorAfterASendDoesNotStopTheBatch() {
        claimed(1L, 2L);
        when(reminders.prepare(1L, NOW)).thenReturn(Optional.of(message(1)));
        when(reminders.prepare(2L, NOW)).thenReturn(Optional.of(message(2)));
        doThrow(new IllegalStateException("connection lost")).when(reminders).markSent(1L, NOW);

        runner.runOnce(NOW);

        verify(reminders, never()).markFailed(eq(1L), any(), any());
        verify(sender).send(message(2));
        verify(reminders).markSent(2L, NOW);
    }
}
