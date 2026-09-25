package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Непрерывный отрезок одной смены с одним видом оплаты. */
public record TimeSegment(LocalDateTime start, LocalDateTime end, TimeType type)
        implements WorkPeriod {
    /** Проверяет корректность границ и вида времени. */
    public TimeSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(type, "type");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Отрезок должен иметь положительную длительность");
        }
    }

    /** @return длительность отрезка */
    @Override
    public Duration duration() {
        return Duration.between(start, end);
    }
}
