package com.kastellosth.appointme.entity;

public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean canTransitionTo(AppointmentStatus target) {
        return this == SCHEDULED && target != SCHEDULED;
    }
}