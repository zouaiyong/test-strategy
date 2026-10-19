package demo.fee.calc;

import demo.fee.enums.StorageFeeTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 纯口径计算：体积重 = 长×宽×高 ÷ 6000 向上取整；阶梯单价按天数区间匹配。
 */
@Component
public class StorageFeeCalculator {

    private static final BigDecimal VOLUME_DIVISOR = new BigDecimal("6000");

    public BigDecimal calc(StorageFeeTypeEnum type, BigDecimal length, BigDecimal width, BigDecimal height, int days) {
        BigDecimal volumeWeight = length.multiply(width).multiply(height)
                .divide(VOLUME_DIVISOR, 0, RoundingMode.CEILING);
        BigDecimal unitPrice = tieredPrice(type, days);
        return volumeWeight.multiply(unitPrice).multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 阶梯：0-30 天、31-60 天、61 天以上三档，B2C 比标准贵 20%。 */
    BigDecimal tieredPrice(StorageFeeTypeEnum type, int days) {
        BigDecimal base;
        if (days <= 30) {
            base = new BigDecimal("0.10");
        } else if (days <= 60) {
            base = new BigDecimal("0.15");
        } else {
            base = new BigDecimal("0.25");
        }
        if (type == StorageFeeTypeEnum.B2C) {
            return base.multiply(new BigDecimal("1.2"));
        }
        return base;
    }
}
