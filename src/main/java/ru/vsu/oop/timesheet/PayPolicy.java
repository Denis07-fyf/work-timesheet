package ru.vsu.oop.timesheet;

import java.math.BigDecimal;

/** Полиморфное правило оплаты отрезка рабочего времени. */
public interface PayPolicy {
    /** @return коэффициент относительно базовой часовой ставки */
    BigDecimal multiplier();
}
