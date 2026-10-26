package com.kastellosth.appointme.dto;

import java.util.List;

public record WorkingHoursResponse(Long staffId, List<ScheduleInterval> intervals) {
}