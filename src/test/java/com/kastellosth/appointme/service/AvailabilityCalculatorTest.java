package com.kastellosth.appointme.service;

import com.kastellosth.appointme.service.AvailabilityCalculator.LocalWindow;
import com.kastellosth.appointme.service.AvailabilityCalculator.Range;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityCalculatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 15);
    private static final Duration HALF_HOUR = Duration.ofMinutes(30);
    private static final Instant LONG_AGO = Instant.EPOCH;

    private static LocalWindow window(int fromHour, int toHour) {
        return new LocalWindow(LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }

    private static Range utc(String from, String to) {
        return new Range(Instant.parse("2030-01-15T" + from + ":00Z"), Instant.parse("2030-01-15T" + to + ":00Z"));
    }

    private static List<Range> slots(List<LocalWindow> working, List<Range> busy, Duration duration) {
        return AvailabilityCalculator.freeSlots(DAY, UTC, working, busy, duration, HALF_HOUR, LONG_AGO);
    }

    @Test
    void shouldFillAnEmptyDay() {
        List<Range> slots = slots(List.of(window(9, 11)), List.of(), HALF_HOUR);

        assertEquals(List.of(utc("09:00", "09:30"), utc("09:30", "10:00"),
                utc("10:00", "10:30"), utc("10:30", "11:00")), slots);
    }

    @Test
    void shouldReturnNothingWhenNotWorking() {
        assertTrue(slots(List.of(), List.of(), HALF_HOUR).isEmpty());
    }

    @Test
    void shouldSkipABookingInTheMiddle() {
        List<Range> slots = slots(List.of(window(9, 11)), List.of(utc("09:30", "10:00")), HALF_HOUR);

        assertEquals(List.of(utc("09:00", "09:30"), utc("10:00", "10:30"), utc("10:30", "11:00")), slots);
    }

    @Test
    void shouldReturnNothingWhenTheServiceIsLongerThanAnyGap() {
        List<Range> slots = slots(List.of(window(9, 11)), List.of(utc("10:00", "10:30")), Duration.ofMinutes(90));

        assertTrue(slots.isEmpty());
    }

    @Test
    void shouldAllowASlotThatTouchesABooking() {
        Range booking = utc("10:00", "10:30");

        assertFalse(utc("09:30", "10:00").overlaps(booking), "ending when it starts is not an overlap");
        assertFalse(utc("10:30", "11:00").overlaps(booking), "starting when it ends is not an overlap");
        assertTrue(utc("10:29", "10:45").overlaps(booking));
    }

    @Test
    void shouldNotOfferASlotThatRunsPastTheEndOfWork() {
        List<Range> slots = slots(List.of(window(9, 10)), List.of(), Duration.ofMinutes(45));

        assertEquals(List.of(new Range(Instant.parse("2030-01-15T09:00:00Z"), Instant.parse("2030-01-15T09:45:00Z"))), slots);
    }

    @Test
    void shouldKeepALunchBreakFree() {
        List<Range> slots = slots(List.of(window(14, 15), window(9, 10)), List.of(), Duration.ofHours(1));

        assertEquals(List.of(utc("09:00", "10:00"), utc("14:00", "15:00")), slots);
    }

    @Test
    void shouldNotOfferSlotsThatHaveAlreadyStarted() {
        List<Range> slots = AvailabilityCalculator.freeSlots(DAY, UTC, List.of(window(9, 10)), List.of(),
                HALF_HOUR, HALF_HOUR, Instant.parse("2030-01-15T09:15:00Z"));

        assertEquals(List.of(utc("09:30", "10:00")), slots);
    }

    @Test
    void shouldUseTheBusinessTimeZone() {
        List<Range> slots = AvailabilityCalculator.freeSlots(DAY, ATHENS, List.of(window(9, 10)), List.of(),
                Duration.ofHours(1), HALF_HOUR, LONG_AGO);

        assertEquals(List.of(utc("07:00", "08:00")), slots, "09:00 in Athens in January is 07:00 UTC");
    }

    @Test
    void shouldHaveFewerSlotsOnTheDayTheClocksGoForward() {
        LocalDate shortDay = LocalDate.of(2026, 3, 29);

        List<Range> slots = AvailabilityCalculator.freeSlots(shortDay, ATHENS, List.of(window(2, 5)), List.of(),
                HALF_HOUR, HALF_HOUR, LONG_AGO);

        assertEquals(4, slots.size(), "02:00 to 05:00 on this day is only two real hours");
    }

    @Test
    void shouldHaveMoreSlotsOnTheDayTheClocksGoBack() {
        LocalDate longDay = LocalDate.of(2026, 10, 25);

        List<Range> slots = AvailabilityCalculator.freeSlots(longDay, ATHENS, List.of(window(2, 5)), List.of(),
                HALF_HOUR, HALF_HOUR, LONG_AGO);

        assertEquals(8, slots.size(), "02:00 to 05:00 on this day is four real hours");
    }

    @Test
    void shouldRefuseADurationOrStepThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> slots(List.of(), List.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> AvailabilityCalculator.freeSlots(
                DAY, UTC, List.of(), List.of(), HALF_HOUR, Duration.ZERO, LONG_AGO));
    }

    @Test
    void shouldCheckThatABookingFitsInsideOneWindow() {
        List<LocalWindow> working = List.of(window(9, 12), window(13, 17));

        assertTrue(AvailabilityCalculator.fitsWithinWindows(DAY, UTC, working, utc("11:30", "12:00")));
        assertFalse(AvailabilityCalculator.fitsWithinWindows(DAY, UTC, working, utc("11:30", "13:30")),
                "must not span the lunch break");
    }
}
