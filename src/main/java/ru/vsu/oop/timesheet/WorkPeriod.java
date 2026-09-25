package ru.vsu.oop.timesheet;

import java.time.Duration;
import java.time.LocalDateTime;

/** Интервал работы: исходная смена или её классифицированный отрезок. */
public sealed interface WorkPeriod permits Shift, TimeSegment {
    /** @return начало интервала включительно */
    LocalDateTime start();

    /** @return конец интервала исключительно */
    LocalDateTime end();

    /** @return длительность интервала */
    Duration duration();
}
