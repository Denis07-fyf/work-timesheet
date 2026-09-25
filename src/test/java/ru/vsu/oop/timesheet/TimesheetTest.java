package ru.vsu.oop.timesheet;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TimesheetTest {
    private static final EmployeeId ID = new EmployeeId(7);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 9, day, hour, minute);
    }

    private static Timesheet sheet(Set<LocalDate> holidays) {
        var sheet = new Timesheet(holidays);
        sheet.register(new Employee(ID, "Анна", 6_000));
        return sheet;
    }

    @Test void identifierEqualityAndHashCode() {
        var first = new EmployeeId(7);
        var same = new EmployeeId(7);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new EmployeeId(8));
        assertTrue(Set.of(first).contains(same));
    }

    @Test void invalidIdentifierAndEmployee() {
        assertThrows(IllegalArgumentException.class, () -> new EmployeeId(0));
        assertThrows(IllegalArgumentException.class, () -> new Employee(ID, " ", 100));
        assertThrows(IllegalArgumentException.class, () -> new Employee(ID, "Анна", -1));
    }

    @Test void shiftRequiresPositiveMinuteAlignedDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new Shift(at(10, 9, 0), at(10, 9, 0), false));
        assertThrows(IllegalArgumentException.class,
                () -> new Shift(at(10, 10, 0), at(10, 9, 0), false));
        assertThrows(IllegalArgumentException.class,
                () -> new Shift(at(10, 9, 0).plusSeconds(1), at(10, 10, 0), false));
    }

    @Test void nightShiftCrossesMidnightAndSix() {
        var parts = sheet(Set.of()).split(new Shift(at(10, 21, 0), at(11, 7, 0), false));
        assertEquals(3, parts.size());
        assertEquals(TimeType.REGULAR, parts.get(0).type());
        assertEquals(Duration.ofHours(1), parts.get(0).duration());
        assertEquals(TimeType.NIGHT, parts.get(1).type());
        assertEquals(Duration.ofHours(8), parts.get(1).duration());
        assertEquals(TimeType.REGULAR, parts.get(2).type());
    }

    @Test void boundariesAtTwentyTwoAndSix() {
        var parts = sheet(Set.of()).split(new Shift(at(10, 22, 0), at(11, 6, 0), false));
        assertEquals(1, parts.size());
        assertEquals(TimeType.NIGHT, parts.get(0).type());
        assertEquals(Duration.ofHours(8), parts.get(0).duration());
    }

    @Test void holidayStartsExactlyAtMidnight() {
        var sheet = sheet(Set.of(LocalDate.of(2026, 9, 11)));
        var parts = sheet.split(new Shift(at(10, 23, 0), at(11, 1, 0), false));
        assertEquals(TimeType.NIGHT, parts.get(0).type());
        assertEquals(TimeType.HOLIDAY, parts.get(1).type());
        assertEquals(Duration.ofHours(1), parts.get(1).duration());
    }

    @Test void holidayOverridesOvertimeAndNight() {
        var sheet = sheet(Set.of(LocalDate.of(2026, 9, 10)));
        var parts = sheet.split(new Shift(at(10, 22, 0), at(10, 23, 0), true));
        assertEquals(1, parts.size());
        assertEquals(TimeType.HOLIDAY, parts.get(0).type());
    }

    @Test void overtimeOverridesNight() {
        var parts = sheet(Set.of()).split(new Shift(at(10, 21, 0), at(10, 23, 0), true));
        assertEquals(1, parts.size());
        assertEquals(TimeType.OVERTIME, parts.get(0).type());
        assertEquals(Duration.ofHours(2), parts.get(0).duration());
    }

    @Test void monthlyReportCalculatesEachTypeAndPay() {
        var sheet = sheet(Set.of(LocalDate.of(2026, 9, 14)));
        sheet.addShift(ID, new Shift(at(9, 9, 0), at(9, 10, 0), false));
        sheet.addShift(ID, new Shift(at(10, 22, 0), at(10, 23, 0), false));
        sheet.addShift(ID, new Shift(at(11, 12, 0), at(11, 13, 0), true));
        sheet.addShift(ID, new Shift(at(14, 12, 0), at(14, 13, 0), false));
        var report = sheet.report(ID, SEPTEMBER);
        for (var type : TimeType.values()) {
            assertEquals(Duration.ofHours(1), report.byType().get(type));
        }
        assertEquals(Duration.ofHours(4), report.totalDuration());
        assertEquals(34_200L, report.paymentKopecks()); // 6000 * (1 + 1.2 + 1.5 + 2)
    }

    @Test void fractionalHourRoundsToKopeck() {
        var sheet = new Timesheet(Set.of());
        var id = new EmployeeId(1);
        sheet.register(new Employee(id, "Игорь", 101));
        sheet.addShift(id, new Shift(at(10, 9, 0), at(10, 9, 1), false));
        assertEquals(2L, sheet.report(id, SEPTEMBER).paymentKopecks());
    }

    @Test void reportClipsShiftAtMonthBoundary() {
        var sheet = sheet(Set.of());
        sheet.addShift(ID, new Shift(LocalDateTime.of(2026, 8, 31, 23, 0), at(1, 1, 0), false));
        assertEquals(Duration.ofHours(1), sheet.report(ID, YearMonth.of(2026, 8)).totalDuration());
        assertEquals(Duration.ofHours(1), sheet.report(ID, SEPTEMBER).totalDuration());
    }

    @Test void workedClipsToRequestedPeriodAndHandlesEmptyPeriod() {
        var sheet = sheet(Set.of());
        sheet.addShift(ID, new Shift(at(10, 9, 0), at(10, 11, 0), false));
        assertEquals(Duration.ofMinutes(30), sheet.worked(ID, at(10, 10, 0), at(10, 10, 30)));
        assertEquals(Duration.ZERO, sheet.worked(ID, at(10, 10, 0), at(10, 10, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> sheet.worked(ID, at(11, 0, 0), at(10, 0, 0)));
    }

    @Test void duplicateIdentifierThrowsCustomException() {
        var sheet = sheet(Set.of());
        var error = assertThrows(TimesheetException.class,
                () -> sheet.register(new Employee(new EmployeeId(7), "Другой", 100)));
        assertTrue(error.getMessage().contains("7"));
    }

    @Test void overlappingShiftThrowsButAdjacentIsAllowed() {
        var sheet = sheet(Set.of());
        sheet.addShift(ID, new Shift(at(10, 9, 0), at(10, 10, 0), false));
        assertThrows(TimesheetException.class,
                () -> sheet.addShift(ID, new Shift(at(10, 9, 30), at(10, 11, 0), false)));
        sheet.addShift(ID, new Shift(at(10, 10, 0), at(10, 11, 0), false));
        assertEquals(2, sheet.shiftsOf(ID).size());
    }

    @Test void unknownEmployeeThrowsCustomException() {
        var sheet = sheet(Set.of());
        assertThrows(TimesheetException.class, () -> sheet.shiftsOf(new EmployeeId(99)));
        assertThrows(TimesheetException.class, () -> sheet.report(new EmployeeId(99), SEPTEMBER));
    }

    @Test void noShiftsProducesZeroReport() {
        var report = sheet(Set.of()).report(ID, SEPTEMBER);
        assertEquals(Duration.ZERO, report.totalDuration());
        assertEquals(0, report.paymentKopecks());
    }

    @Test void mutableInputAndInternalCollectionsCannotEscape() {
        var holidays = new HashSet<LocalDate>();
        var sheet = sheet(holidays);
        holidays.add(LocalDate.of(2026, 9, 10));
        assertEquals(TimeType.REGULAR,
                sheet.split(new Shift(at(10, 12, 0), at(10, 13, 0), false)).get(0).type());
        assertThrows(UnsupportedOperationException.class, () -> sheet.employees().clear());
        assertThrows(UnsupportedOperationException.class, () -> sheet.shiftsOf(ID).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> sheet.report(ID, SEPTEMBER).byType().clear());
    }
}
