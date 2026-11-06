package com.kastellosth.appointme.service;

/**
 * May be called twice for one reminder after a crash. A real provider should receive
 * reminderId as its idempotency key.
 */
public interface ReminderSender {

    void send(ReminderMessage message);
}
