package ru.vsu.oop.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Консольный пример всех операций базового уровня. */
public final class Demo {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Demo() { }

    /** Наполняет табель данными и выводит результаты в консоль.
     * @param args аргументы командной строки не используются
     */
    public static void main(String[] args) {
        var holiday = LocalDate.of(2026, 9, 14); // условный праздник для примера
        var sheet = new Timesheet(Set.of(holiday));
        for (int i = 1; i <= 8; i++) {
            var id = new EmployeeId(i);
            sheet.register(new Employee(id, "Сотрудник " + i, 30_000L + i * 2_500L));
            sheet.addShift(id, shift(9, 9, 0, 9, 17, 0, false));
            sheet.addShift(id, shift(10, 20, 0, 11, 3, 0, false));
            sheet.addShift(id, shift(12, 12, 0, 12, 16, 0, true));
            sheet.addShift(id, shift(14, 8, 0, 14, 12, 0, false));
        }
        System.out.println("Табель: " + sheet.employees().size()
                + " сотрудников и " + sheet.employees().stream()
                        .mapToInt(e -> sheet.shiftsOf(e.id()).size()).sum() + " смен.");
        System.out.println("14 сентября — условный праздник демонстрации.");

        var id = new EmployeeId(1);
        var nightShift = sheet.shiftsOf(id).get(1);
        System.out.println("\nРазбиение смены " + format(nightShift.start())
                + " — " + format(nightShift.end()) + ":");
        for (var part : sheet.split(nightShift)) {
            System.out.printf("  %s — %s: %s, %s мин.%n", format(part.start()),
                    format(part.end()), part.type(), part.duration().toMinutes());
        }

        List<WorkPeriod> periods = List.of(nightShift, sheet.split(nightShift).get(0));
        System.out.println("\nПолиморфный расчёт длительности смены и её отрезка:");
        for (var period : periods) {
            System.out.println("  " + period.getClass().getSimpleName() + ": "
                    + period.duration().toMinutes() + " мин.");
        }

        System.out.println("\nВремя сотрудника 1 за 10–11 сентября: "
                + sheet.worked(id, at(10, 0, 0), at(12, 0, 0)).toHours() + " ч.");
        System.out.println("\nМесячные итоги и оплата:");
        for (var employee : sheet.employees().stream().sorted((a, b) ->
                Integer.compare(a.id().value(), b.id().value())).toList()) {
            var report = sheet.report(employee.id(), YearMonth.of(2026, 9));
            System.out.println(employee.name() + " — " + report.totalDuration().toHours()
                    + " ч., " + report.paymentKopecks() + " коп.");
            if (employee.id().equals(id)) {
                for (var type : TimeType.values()) {
                    System.out.println("  " + type + ": "
                            + report.byType().get(type).toMinutes() + " мин., коэффициент "
                            + type.multiplier());
                }
            }
        }
        try {
            sheet.addShift(id, shift(10, 21, 0, 10, 22, 0, false));
        } catch (TimesheetException exception) {
            System.out.println("\nПроверка ошибки: " + exception.getMessage());
        }
    }

    private static String format(LocalDateTime time) {
        return FORMAT.format(time);
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 9, day, hour, minute);
    }

    private static Shift shift(int dayStart, int hourStart, int minuteStart,
                               int dayEnd, int hourEnd, int minuteEnd, boolean overtime) {
        return new Shift(at(dayStart, hourStart, minuteStart),
                at(dayEnd, hourEnd, minuteEnd), overtime);
    }
}
