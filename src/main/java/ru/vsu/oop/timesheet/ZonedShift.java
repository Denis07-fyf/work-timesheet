package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Смена в часовом поясе, сохраняющая реальные часы при переводе времени.
 * @param start начало включительно
 * @param end конец исключительно
 * @param overtime признак назначенной сверхурочной работы
 */
public record ZonedShift(ZonedDateTime start, ZonedDateTime end, boolean overtime) {
    /** Проверяет часовой пояс, хронологию и точность до минуты. */
    public ZonedShift {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.getZone().equals(end.getZone())) {
            throw new IllegalArgumentException("Начало и конец должны иметь один часовой пояс");
        }
        if (!end.toInstant().isAfter(start.toInstant())) {
            throw new IllegalArgumentException("Конец смены должен быть позже начала");
        }
        if (start.getSecond() != 0 || start.getNano() != 0
                || end.getSecond() != 0 || end.getNano() != 0) {
            throw new IllegalArgumentException("Границы смены задаются с точностью до минуты");
        }
    }

    /** Вычисляет фактическое время между двумя моментами с учётом перевода часов.
     * @return длительность смены по часовой шкале
     */
    public Duration duration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}
