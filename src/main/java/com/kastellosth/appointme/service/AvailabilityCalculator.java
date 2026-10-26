package com.kastellosth.appointme.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public record LocalWindow(LocalTime start, LocalTime end) {
    }

    public record Range(Instant start, Instant end) {

        public boolean overlaps(Range other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    /**
     * @param date      the calendar day in the TENANT's zone
     * @param zone      the tenant's time zone
     * @param working   the staff member's wall-clock intervals for that weekday
     * @param busy      existing (non-cancelled) appointments as real ranges
     * @param duration  how long the service takes
     * @param step      distance between candidate start times
     * @param notBefore slots starting before this moment are dropped (normally "now")
     * @return free slots in chronological order, each exactly {@code duration} long
     */
    public static List<Range> freeSlots(LocalDate date, ZoneId zone,
                                        List<LocalWindow> working, List<Range> busy,
                                        Duration duration, Duration step, Instant notBefore) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("step must be positive");
        }

        List<Range> windows = working.stream()
                .map(w -> new Range(toInstant(date, w.start(), zone), toInstant(date, w.end(), zone)))
                .filter(r -> r.end().isAfter(r.start()))
                .sorted(Comparator.comparing(Range::start))
                .toList();

        List<Range> slots = new ArrayList<>();
        for (Range window : windows) {

            for (Instant start = window.start(); ; start = start.plus(step)) {
                Instant end = start.plus(duration);
                if (end.isAfter(window.end())) {
                    break;
                }
                if (start.isBefore(notBefore)) {
                    continue;
                }
                Range candidate = new Range(start, end);
                if (busy.stream().noneMatch(candidate::overlaps)) {
                    slots.add(candidate);
                }
            }
        }
        return slots;
    }

    static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    public static boolean fitsWithinWindows(LocalDate date, ZoneId zone,
                                            List<LocalWindow> working, Range range) {
        return working.stream()
                .map(w -> new Range(toInstant(date, w.start(), zone), toInstant(date, w.end(), zone)))
                .anyMatch(w -> !range.start().isBefore(w.start()) && !range.end().isAfter(w.end()));
    }
}