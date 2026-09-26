package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Табель одного сотрудника в часовом поясе с месячным пределом рабочих часов.
 * Для периода перехода на летнее и зимнее время считает фактические минуты.
 */
public final class ZonedTimesheet {
    private final Employee employee;
    private final ZoneId zone;
    private final Set<LocalDate> holidays;
    private final Duration monthlyNorm;
    private final Duration allowedOvertime;
    private final List<ZonedShift> shifts = new ArrayList<>();

    /** Создаёт табель и задаёт максимально допустимое время за месяц как норму
     * плюс разрешённую переработку.
     * @param employee сотрудник
     * @param zone часовой пояс для классификации дней и часов
     * @param holidays праздничные даты в этом часовом поясе
     * @param monthlyNorm месячная норма времени, неотрицательная
     * @param allowedOvertime допустимая переработка сверх нормы, неотрицательная
     */
    public ZonedTimesheet(Employee employee, ZoneId zone, Set<LocalDate> holidays,
                          Duration monthlyNorm, Duration allowedOvertime) {
        this.employee = Objects.requireNonNull(employee, "employee");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.holidays = Set.copyOf(holidays);
        this.monthlyNorm = Objects.requireNonNull(monthlyNorm, "monthlyNorm");
        this.allowedOvertime = Objects.requireNonNull(allowedOvertime, "allowedOvertime");
        if (monthlyNorm.isNegative() || allowedOvertime.isNegative()) {
            throw new IllegalArgumentException("Норма и разрешённая переработка неотрицательны");
        }
        monthlyNorm.plus(allowedOvertime); // также проверяет переполнение Duration
    }

    /** Добавляет смену, предварительно проверив все затронутые месяцы.
     * При ошибке табель остаётся без изменений.
     * @param shift новая смена в часовом поясе табеля
     * @throws TimesheetException если смена пересекается с имеющейся или её пояс неверен
     * @throws OvertimeLimitException если месячный предел будет превышен
     */
    public void addShift(ZonedShift shift) {
        Objects.requireNonNull(shift, "shift");
        if (!shift.start().getZone().equals(zone)) {
            throw new TimesheetException("Часовой пояс смены отличается от пояса табеля");
        }
        for (var existing : shifts) {
            if (shift.start().isBefore(existing.end()) && existing.start().isBefore(shift.end())) {
                throw new TimesheetException("Смены сотрудника пересекаются");
            }
        }
        var limit = monthlyNorm.plus(allowedOvertime);
        var month = YearMonth.from(shift.start());
        var lastMonth = YearMonth.from(shift.end());
        while (!month.isAfter(lastMonth)) {
            var proposed = durationInMonth(shift, month);
            for (var existing : shifts) {
                proposed = proposed.plus(durationInMonth(existing, month));
            }
            if (proposed.compareTo(limit) > 0) {
                throw new OvertimeLimitException("Превышен лимит за " + month + ": "
                        + proposed.toMinutes() + " мин. при пределе " + limit.toMinutes() + " мин.");
            }
            month = month.plusMonths(1);
        }
        shifts.add(shift);
    }

    /** Возвращает неизменяемый снимок записанных смен.
     * @return список смен
     */
    public List<ZonedShift> shifts() {
        return List.copyOf(shifts);
    }

    /** Делит смену на интервалы с одним видом оплаты по фактической временной шкале.
     * Минутные шаги сохраняют обе версии повторяющегося часа и пропускают
     * несуществующий час при переводе часов.
     * @param shift смена в часовом поясе табеля
     * @return хронологические интервалы
     */
    public List<ZonedTimeSegment> split(ZonedShift shift) {
        Objects.requireNonNull(shift, "shift");
        if (!shift.start().getZone().equals(zone)) {
            throw new IllegalArgumentException("Часовой пояс смены отличается от пояса табеля");
        }
        var result = new ArrayList<ZonedTimeSegment>();
        var cursor = shift.start();
        while (cursor.isBefore(shift.end())) {
            var next = cursor.plusMinutes(1);
            if (next.isAfter(shift.end())) {
                next = shift.end();
            }
            var type = classify(cursor, shift.overtime());
            if (!result.isEmpty()) {
                var last = result.get(result.size() - 1);
                if (last.type() == type) {
                    result.set(result.size() - 1,
                            new ZonedTimeSegment(last.start(), next, type));
                } else {
                    result.add(new ZonedTimeSegment(cursor, next, type));
                }
            } else {
                result.add(new ZonedTimeSegment(cursor, next, type));
            }
            cursor = next;
        }
        return List.copyOf(result);
    }

    /** Формирует месячный отчёт по фактически прошедшим минутам.
     * @param month отчётный месяц в часовом поясе табеля
     * @return отчёт по видам времени и оплате
     */
    public MonthlyReport report(YearMonth month) {
        Objects.requireNonNull(month, "month");
        var totals = new EnumMap<TimeType, Duration>(TimeType.class);
        for (var type : TimeType.values()) {
            totals.put(type, Duration.ZERO);
        }
        var beginning = beginning(month);
        var ending = beginning(month.plusMonths(1));
        long payment = 0;
        for (var shift : shifts) {
            if (!shift.start().isBefore(ending) || !shift.end().isAfter(beginning)) {
                continue;
            }
            for (var part : split(shift)) {
                var start = later(part.start(), beginning);
                var end = earlier(part.end(), ending);
                if (start.isBefore(end)) {
                    var duration = Duration.between(start, end);
                    totals.merge(part.type(), duration, Duration::plus);
                    payment = Math.addExact(payment,
                            part.type().paymentKopecks(duration, employee.hourlyRateKopecks()));
                }
            }
        }
        return new MonthlyReport(employee, month, totals, payment);
    }

    private TimeType classify(ZonedDateTime time, boolean overtime) {
        if (holidays.contains(time.toLocalDate())) return TimeType.HOLIDAY;
        if (overtime) return TimeType.OVERTIME;
        var hour = time.getHour();
        return hour >= 22 || hour < 6 ? TimeType.NIGHT : TimeType.REGULAR;
    }

    private Duration durationInMonth(ZonedShift shift, YearMonth month) {
        var start = later(shift.start(), beginning(month));
        var end = earlier(shift.end(), beginning(month.plusMonths(1)));
        return start.isBefore(end) ? Duration.between(start, end) : Duration.ZERO;
    }

    private ZonedDateTime beginning(YearMonth month) {
        return month.atDay(1).atStartOfDay(zone);
    }

    private static ZonedDateTime earlier(ZonedDateTime a, ZonedDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static ZonedDateTime later(ZonedDateTime a, ZonedDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
