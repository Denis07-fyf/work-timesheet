package ru.vsu.oop.timesheet;

/** Ошибка регистрации сотрудника или смены в табеле. */
public final class TimesheetException extends RuntimeException {
    /** @param message понятное пользователю описание причины */
    public TimesheetException(String message) {
        super(message);
    }
}
