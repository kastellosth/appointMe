package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.ScheduleInterval;
import com.kastellosth.appointme.exception.BadRequestException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

public final class WorkingHoursValidator {

    private WorkingHoursValidator() {
    }

    public static void validate(List<ScheduleInterval> intervals) {
        // 1) every interval must end after it starts
        for (ScheduleInterval i : intervals) {
            if (!i.end().isAfter(i.start())) {
                throw new BadRequestException(i.dayOfWeek() + ": end time " + i.end()
                        + " must be after start time " + i.start());
            }
        }

        // 2) no overlaps within a day: sort by day then start, and compare each
        //    start with the latest end seen so far on that day
        List<ScheduleInterval> sorted = intervals.stream()
                .sorted(Comparator.comparing(ScheduleInterval::dayOfWeek)
                        .thenComparing(ScheduleInterval::start))
                .toList();

        DayOfWeek currentDay = null;
        LocalTime latestEnd = null;
        for (ScheduleInterval i : sorted) {
            if (i.dayOfWeek() != currentDay) {
                currentDay = i.dayOfWeek();
                latestEnd = i.end();
                continue;
            }
            if (i.start().isBefore(latestEnd)) {
                throw new BadRequestException(i.dayOfWeek() + ": interval starting at " + i.start()
                        + " overlaps an earlier interval that ends at " + latestEnd);
            }
            if (i.end().isAfter(latestEnd)) {
                latestEnd = i.end();
            }
        }
    }
}