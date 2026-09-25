package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.LocalDateTime;

/** Интервал работы: исходная смена или её классифицированный отрезок. */
public sealed interface WorkPeriod permits Shift, TimeSegment {
    /** Возвращает левую границу интервала.
     * @return начало интервала включительно
     */
    LocalDateTime start();

    /** Возвращает правую границу интервала.
     * @return конец интервала исключительно
     */
    LocalDateTime end();

    /** Вычисляет время между границами.
     * @return длительность интервала
     */
    Duration duration();
}
