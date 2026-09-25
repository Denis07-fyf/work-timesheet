package ru.vsu.oop.timesheet;

import java.math.BigDecimal;

/** Взаимоисключающие виды рабочего времени. */
public enum TimeType implements PayPolicy {
    /** Обычная работа. */
    REGULAR { public BigDecimal multiplier() { return BigDecimal.ONE; } },
    /** Работа по заранее обозначенной сверхурочной смене. */
    OVERTIME { public BigDecimal multiplier() { return new BigDecimal("1.5"); } },
    /** Работа с 22:00 до 06:00. */
    NIGHT { public BigDecimal multiplier() { return new BigDecimal("1.2"); } },
    /** Работа в отмеченный календарный праздник. */
    HOLIDAY { public BigDecimal multiplier() { return new BigDecimal("2.0"); } };

    /** @return коэффициент оплаты для данного вида времени */
    @Override
    public abstract BigDecimal multiplier();
}
