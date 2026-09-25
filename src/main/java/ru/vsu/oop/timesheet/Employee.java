package ru.vsu.oop.timesheet;

import java.util.Objects;

/** Сотрудник и его базовая ставка в копейках за час. */
public record Employee(EmployeeId id, String name, long hourlyRateKopecks) {
    /** Проверяет данные сотрудника. */
    public Employee {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя сотрудника не может быть пустым");
        }
        if (hourlyRateKopecks < 0) {
            throw new IllegalArgumentException("Ставка не может быть отрицательной");
        }
    }
}
