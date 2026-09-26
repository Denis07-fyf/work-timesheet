package ru.vsu.oop.timesheet;

/** Превышена сумма месячной нормы и разрешённой переработки. */
public final class OvertimeLimitException extends TimesheetException {
    /** Создаёт ошибку превышения лимита.
     * @param message сообщение с месяцем и допустимыми часами
     */
    public OvertimeLimitException(String message) {
        super(message);
    }
}
