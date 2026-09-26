package ru.vsu.oop.timesheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;

/** Полиморфное правило оплаты отрезка рабочего времени. */
public interface PayPolicy {
    /** Определяет множитель оплаты.
     * @return коэффициент относительно базовой часовой ставки
     */
    BigDecimal multiplier();

    /** Считает оплату целого числа рабочих минут.
     * @param duration длительность отрезка
     * @param hourlyRateKopecks базовая ставка в копейках за час
     * @return сумма в копейках с округлением каждого отрезка HALF_UP
     */
    default long paymentKopecks(Duration duration, long hourlyRateKopecks) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.getSeconds() % 60 != 0
                || duration.getNano() != 0 || hourlyRateKopecks < 0) {
            throw new IllegalArgumentException("Нужны целые минуты и неотрицательная ставка");
        }
        return BigDecimal.valueOf(hourlyRateKopecks)
                .multiply(multiplier())
                .multiply(BigDecimal.valueOf(duration.toMinutes()))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
