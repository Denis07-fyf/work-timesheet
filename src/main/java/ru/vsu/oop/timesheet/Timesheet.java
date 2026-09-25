package ru.vsu.oop.timesheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Хранит смены в памяти и формирует отчёты о рабочем времени. */
public final class Timesheet {
    private final Map<EmployeeId, Employee> employees = new HashMap<>();
    private final Map<EmployeeId, List<Shift>> shifts = new HashMap<>();
    private final Set<LocalDate> holidays;

    /** Создаёт пустой табель с заданным календарём праздников.
     * @param holidays заданные извне праздничные даты; набор копируется
     */
    public Timesheet(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /** Регистрирует сотрудника с уникальным номером.
     * @param employee новый сотрудник
     * @throws TimesheetException если номер уже занят
     */
    public void register(Employee employee) {
        Objects.requireNonNull(employee, "employee");
        if (employees.containsKey(employee.id())) {
            throw new TimesheetException("Сотрудник с номером " + employee.id().value() + " уже есть");
        }
        employees.put(employee.id(), employee);
        shifts.put(employee.id(), new ArrayList<>());
    }

    /** Возвращает зарегистрированных сотрудников.
     * @return снимок списка зарегистрированных сотрудников
     */
    public List<Employee> employees() {
        return List.copyOf(employees.values());
    }

    /** Добавляет смену без пересечений с другими сменами сотрудника.
     * @param id номер сотрудника
     * @param shift смена
     * @throws TimesheetException если сотрудник неизвестен или смены пересекаются
     */
    public void addShift(EmployeeId id, Shift shift) {
        Objects.requireNonNull(shift, "shift");
        var current = shiftsFor(id);
        for (var existing : current) {
            if (shift.start().isBefore(existing.end()) && existing.start().isBefore(shift.end())) {
                throw new TimesheetException("Смена пересекается с уже записанной сменой сотрудника "
                        + id.value());
            }
        }
        current.add(shift);
    }

    /** Возвращает сохранённые смены сотрудника.
     * @param id номер сотрудника
     * @return неизменяемый снимок его смен
     * @throws TimesheetException если сотрудник неизвестен
     */
    public List<Shift> shiftsOf(EmployeeId id) {
        return List.copyOf(shiftsFor(id));
    }

    /** Делит смену на интервалы обычного, ночного, сверхурочного и праздничного времени.
     * Праздник имеет приоритет над сверхурочным временем, сверхурочное — над ночным.
     * Смена и возвращённые интервалы не меняют состояние табеля.
     * @param shift смена для анализа
     * @return интервалы в хронологическом порядке
     */
    public List<TimeSegment> split(Shift shift) {
        Objects.requireNonNull(shift, "shift");
        var result = new ArrayList<TimeSegment>();
        var cursor = shift.start();
        while (cursor.isBefore(shift.end())) {
            var next = shift.end();
            next = earlier(next, cursor.toLocalDate().plusDays(1).atStartOfDay());
            next = earlier(next, followingBoundary(cursor, LocalTime.of(6, 0)));
            next = earlier(next, followingBoundary(cursor, LocalTime.of(22, 0)));
            var type = classify(cursor, shift.overtime());
            if (!result.isEmpty()) {
                var last = result.get(result.size() - 1);
                if (last.type() == type && last.end().equals(cursor)) {
                    result.set(result.size() - 1, new TimeSegment(last.start(), next, type));
                } else {
                    result.add(new TimeSegment(cursor, next, type));
                }
            } else {
                result.add(new TimeSegment(cursor, next, type));
            }
            cursor = next;
        }
        return List.copyOf(result);
    }

    /** Суммирует время сотрудника на полуоткрытом интервале [from, to).
     * @param id номер сотрудника
     * @param from начало периода
     * @param to конец периода
     * @return длительность пересечений смен с периодом
     * @throws TimesheetException если сотрудник неизвестен
     */
    public Duration worked(EmployeeId id, LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Конец периода раньше начала");
        }
        var total = Duration.ZERO;
        for (var shift : shiftsFor(id)) {
            var start = later(from, shift.start());
            var end = earlier(to, shift.end());
            if (start.isBefore(end)) {
                total = total.plus(Duration.between(start, end));
            }
        }
        return total;
    }

    /** Формирует отчёт по видам времени и оплате в копейках.
     * Учитываются части смен, попадающие в указанный месяц. Каждый отрезок
     * оплачивается отдельно, с округлением до ближайшей копейки.
     * @param id номер сотрудника
     * @param month отчётный месяц
     * @return неизменяемый месячный отчёт
     * @throws TimesheetException если сотрудник неизвестен
     */
    public MonthlyReport report(EmployeeId id, YearMonth month) {
        Objects.requireNonNull(month, "month");
        var current = shiftsFor(id);
        var totals = new EnumMap<TimeType, Duration>(TimeType.class);
        for (var type : TimeType.values()) {
            totals.put(type, Duration.ZERO);
        }
        var startMonth = month.atDay(1).atStartOfDay();
        var endMonth = month.plusMonths(1).atDay(1).atStartOfDay();
        long payment = 0;
        for (var shift : current) {
            for (var segment : split(shift)) {
                var start = later(segment.start(), startMonth);
                var end = earlier(segment.end(), endMonth);
                if (start.isBefore(end)) {
                    var duration = Duration.between(start, end);
                    totals.merge(segment.type(), duration, Duration::plus);
                    payment = Math.addExact(payment,
                            pay(segment.type(), duration, employees.get(id).hourlyRateKopecks()));
                }
            }
        }
        return new MonthlyReport(employees.get(id), month, totals, payment);
    }

    private static long pay(TimeType type, Duration duration, long rate) {
        return BigDecimal.valueOf(rate)
                .multiply(type.multiplier())
                .multiply(BigDecimal.valueOf(duration.toMinutes()))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private TimeType classify(LocalDateTime time, boolean overtime) {
        if (holidays.contains(time.toLocalDate())) return TimeType.HOLIDAY;
        if (overtime) return TimeType.OVERTIME;
        if (time.toLocalTime().isBefore(LocalTime.of(6, 0))
                || !time.toLocalTime().isBefore(LocalTime.of(22, 0))) return TimeType.NIGHT;
        return TimeType.REGULAR;
    }

    private List<Shift> shiftsFor(EmployeeId id) {
        Objects.requireNonNull(id, "id");
        var current = shifts.get(id);
        if (current == null) {
            throw new TimesheetException("Сотрудник с номером " + id.value() + " не найден");
        }
        return current;
    }

    private static LocalDateTime followingBoundary(LocalDateTime time, LocalTime boundary) {
        var candidate = time.toLocalDate().atTime(boundary);
        return candidate.isAfter(time) ? candidate : candidate.plusDays(1);
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
