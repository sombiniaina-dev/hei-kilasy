package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class GradeCalculator {

    public static boolean isLate(LocalDate deadline, LocalDateTime submittedAt) {
        return deadline != null && submittedAt.toLocalDate().isAfter(deadline);
    }

    public static BigDecimal penalizedGradeOutOf20(BigDecimal rawPoints, BigDecimal maxPoints, LocalDate deadline, LocalDateTime submittedAt) {
        BigDecimal base20 = rawPoints
                .multiply(new BigDecimal("20"))
                .divide(maxPoints, 1, RoundingMode.HALF_UP);
        long daysLate = ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate());
        if (daysLate <= 0) {
            return base20.max(BigDecimal.ZERO);
        }
        BigDecimal penaltyRate =
                BigDecimal.valueOf(daysLate)
                        .multiply(new BigDecimal("0.10"))
                        .min(new BigDecimal("0.50"));
        BigDecimal penalized = base20.multiply(BigDecimal.ONE.subtract(penaltyRate)).setScale(1, RoundingMode.HALF_UP);
        return penalized.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : penalized;
    }
}
