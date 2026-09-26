package ru.vsu.oop.timesheet;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZonedTimesheetTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Employee EMPLOYEE = new Employee(new EmployeeId(1), "Ирина", 6_000);

    private static ZonedDateTime at(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0).atZone(BERLIN);
    }

    private static ZonedTimesheet sheet(Duration norm, Duration extra) {
        return new ZonedTimesheet(EMPLOYEE, BERLIN, Set.of(), norm, extra);
    }

    @Test void springClockJumpMakesNightSevenRealHours() {
        var sheet = sheet(Duration.ofHours(8), Duration.ZERO);
        var shift = new ZonedShift(at(3, 28, 22), at(3, 29, 6), false);
        assertEquals(Duration.ofHours(7), shift.duration());
        sheet.addShift(shift);
        var report = sheet.report(YearMonth.of(2026, 3));
        assertEquals(Duration.ofHours(7), report.byType().get(TimeType.NIGHT));
        assertEquals(50_400, report.paymentKopecks());
        assertEquals(1, sheet.split(shift).size());
    }

    @Test void autumnRepeatedHourMakesNightNineRealHours() {
        var sheet = sheet(Duration.ofHours(9), Duration.ZERO);
        var shift = new ZonedShift(at(10, 24, 22), at(10, 25, 6), false);
        sheet.addShift(shift);
        assertEquals(Duration.ofHours(9), shift.duration());
        assertEquals(Duration.ofHours(9), sheet.report(YearMonth.of(2026, 10)).totalDuration());
        assertEquals(64_800, sheet.report(YearMonth.of(2026, 10)).paymentKopecks());
    }

    @Test void exactMonthlyLimitAcceptedAndNextMinuteRejectedWithoutMutation() {
        var sheet = sheet(Duration.ofHours(8), Duration.ofHours(2));
        sheet.addShift(new ZonedShift(at(9, 10, 8), at(9, 10, 16), false));
        sheet.addShift(new ZonedShift(at(9, 11, 8), at(9, 11, 10), true));
        var error = assertThrows(OvertimeLimitException.class,
                () -> sheet.addShift(new ZonedShift(at(9, 12, 8),
                        at(9, 12, 8).plusMinutes(1), true)));
        assertTrue(error.getMessage().contains("2026-09"));
        assertEquals(2, sheet.shifts().size());
        assertEquals(Duration.ofHours(10), sheet.report(YearMonth.of(2026, 9)).totalDuration());
    }

    @Test void dstElapsedTimeControlsNormNotWallClockDifference() {
        var spring = sheet(Duration.ofHours(7), Duration.ZERO);
        spring.addShift(new ZonedShift(at(3, 28, 22), at(3, 29, 6), false));
        var autumn = sheet(Duration.ofHours(8), Duration.ZERO);
        assertThrows(OvertimeLimitException.class,
                () -> autumn.addShift(new ZonedShift(at(10, 24, 22), at(10, 25, 6), false)));
        assertTrue(autumn.shifts().isEmpty());
    }

    @Test void midnightCrossingMonthIsChargedToBothMonths() {
        var sheet = sheet(Duration.ofHours(1), Duration.ZERO);
        sheet.addShift(new ZonedShift(at(1, 31, 23), at(2, 1, 1), false));
        assertEquals(Duration.ofHours(1), sheet.report(YearMonth.of(2026, 1)).totalDuration());
        assertEquals(Duration.ofHours(1), sheet.report(YearMonth.of(2026, 2)).totalDuration());
        assertThrows(OvertimeLimitException.class,
                () -> sheet.addShift(new ZonedShift(at(2, 1, 1), at(2, 1, 2), false)));
    }

    @Test void overlappingShiftsRejectedButAdjacentShiftsAllowed() {
        var sheet = sheet(Duration.ofHours(10), Duration.ZERO);
        sheet.addShift(new ZonedShift(at(9, 10, 8), at(9, 10, 9), false));
        assertThrows(TimesheetException.class,
                () -> sheet.addShift(new ZonedShift(at(9, 10, 8).plusMinutes(30),
                        at(9, 10, 9).plusMinutes(30), false)));
        sheet.addShift(new ZonedShift(at(9, 10, 9), at(9, 10, 10), false));
        assertEquals(2, sheet.shifts().size());
    }

    @Test void wrongZoneRejectedBeforeAdding() {
        var sheet = sheet(Duration.ofHours(10), Duration.ZERO);
        var paris = ZoneId.of("Europe/Paris");
        assertThrows(TimesheetException.class,
                () -> sheet.addShift(new ZonedShift(
                        LocalDateTime.of(2026, 9, 10, 9, 0).atZone(paris),
                        LocalDateTime.of(2026, 9, 10, 10, 0).atZone(paris), false)));
        assertEquals(0, sheet.shifts().size());
    }

    @Test void shiftValidatesZoneOrderAndMinutePrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZonedShift(at(9, 10, 9), at(9, 10, 9), false));
        assertThrows(IllegalArgumentException.class,
                () -> new ZonedShift(at(9, 10, 10), at(9, 10, 9), false));
        assertThrows(IllegalArgumentException.class,
                () -> new ZonedShift(at(9, 10, 9).plusSeconds(1), at(9, 10, 10), false));
        assertThrows(IllegalArgumentException.class,
                () -> new ZonedShift(at(9, 10, 9), at(9, 10, 10).withZoneSameInstant(ZoneId.of("UTC")), false));
    }

    @Test void negativeLimitsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> sheet(Duration.ofMinutes(-1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> sheet(Duration.ZERO, Duration.ofMinutes(-1)));
    }

    @Test void holidayOverridesNightAndOvertimeAndInputIsCopied() {
        var holidays = new HashSet<LocalDate>();
        holidays.add(LocalDate.of(2026, 9, 10));
        var sheet = new ZonedTimesheet(EMPLOYEE, BERLIN, holidays,
                Duration.ofHours(10), Duration.ZERO);
        holidays.clear();
        var shift = new ZonedShift(at(9, 10, 22), at(9, 10, 23), true);
        sheet.addShift(shift);
        assertEquals(TimeType.HOLIDAY, sheet.split(shift).get(0).type());
        assertEquals(12_000, sheet.report(YearMonth.of(2026, 9)).paymentKopecks());
    }

    @Test void returnedShiftsCannotBeModified() {
        var sheet = sheet(Duration.ofHours(10), Duration.ZERO);
        assertThrows(UnsupportedOperationException.class, () -> sheet.shifts().clear());
    }

    @Test void emptyMonthHasZeroTotals() {
        var sheet = sheet(Duration.ofHours(10), Duration.ZERO);
        var report = sheet.report(YearMonth.of(2026, 9));
        assertEquals(Duration.ZERO, report.totalDuration());
        assertEquals(0, report.paymentKopecks());
    }
}
