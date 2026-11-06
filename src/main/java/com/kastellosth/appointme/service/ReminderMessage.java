package com.kastellosth.appointme.service;

public record ReminderMessage(Long reminderId, String toEmail, String businessName, String serviceName,
                              String localStartTime) {
}
