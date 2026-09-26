package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Отрезок смены в часовом поясе с одним видом оплаты.
 * @param start начало включительно
 * @param end конец исключительно
 * @param type вид времени
 */
public record ZonedTimeSegment(ZonedDateTime start, ZonedDateTime end, TimeType type) {
    /** Проверяет хронологию и вид времени. */
    public ZonedTimeSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(type, "type");
        if (!start.getZone().equals(end.getZone())
                || !end.toInstant().isAfter(start.toInstant())) {
            throw new IllegalArgumentException("Некорректный отрезок в часовом поясе");
        }
    }

    /** Вычисляет фактически отработанное время.
     * @return длительность отрезка
     */
    public Duration duration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}
