package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Смена с пометкой о назначенной сверхурочной работе.
 * @param start начало включительно
 * @param end конец исключительно
 * @param overtime признак назначенной сверхурочной работы
 */
public record Shift(LocalDateTime start, LocalDateTime end, boolean overtime) implements WorkPeriod {
    /** Проверяет порядок дат и минутную точность границ. */
    public Shift {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Конец смены должен быть позже начала");
        }
        if (start.getSecond() != 0 || start.getNano() != 0
                || end.getSecond() != 0 || end.getNano() != 0) {
            throw new IllegalArgumentException("Границы смены задаются с точностью до минуты");
        }
    }

    /** @return длительность смены, в том числе через полночь */
    @Override
    public Duration duration() {
        return Duration.between(start, end);
    }
}
