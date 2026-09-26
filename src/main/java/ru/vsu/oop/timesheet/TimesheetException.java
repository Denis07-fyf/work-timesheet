package ru.vsu.oop.timesheet;

/** Ошибка регистрации сотрудника или смены в табеле. */
public class TimesheetException extends RuntimeException {
    /** Создаёт ошибку табеля с объяснением причины.
     * @param message понятное пользователю описание причины
     */
    public TimesheetException(String message) {
        super(message);
    }
}
