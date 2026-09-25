package ru.vsu.oop.timesheet;

/** Уникальный номер сотрудника. Record предоставляет согласованные equals и hashCode. */
public record EmployeeId(int value) {
    /** Проверяет положительность номера. */
    public EmployeeId {
        if (value <= 0) {
            throw new IllegalArgumentException("Номер сотрудника должен быть положительным");
        }
    }
}
