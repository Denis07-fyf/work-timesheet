package ru.vsu.oop.timesheet;

import java.math.BigDecimal;

/** Полиморфное правило оплаты отрезка рабочего времени. */
public interface PayPolicy {
    /** Определяет множитель оплаты.
     * @return коэффициент относительно базовой часовой ставки
     */
    BigDecimal multiplier();
}
