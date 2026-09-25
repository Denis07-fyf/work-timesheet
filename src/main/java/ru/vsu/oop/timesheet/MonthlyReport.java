package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

/** Неизменяемые итоги по одному сотруднику за календарный месяц. */
public record MonthlyReport(Employee employee, YearMonth month,
                            Map<TimeType, Duration> byType, long paymentKopecks) {
    /** Создаёт защитную копию итогов и проверяет сумму оплаты. */
    public MonthlyReport {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(month, "month");
        byType = Map.copyOf(byType);
        if (paymentKopecks < 0) {
            throw new IllegalArgumentException("Оплата не может быть отрицательной");
        }
    }

    /** @return всё отработанное время за месяц */
    public Duration totalDuration() {
        return byType.values().stream().reduce(Duration.ZERO, Duration::plus);
    }
}
