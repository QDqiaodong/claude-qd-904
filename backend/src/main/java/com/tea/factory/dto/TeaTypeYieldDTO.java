package com.tea.factory.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按茶类统计投料量与产出率（JPQL 投影）。
 */
public class TeaTypeYieldDTO {

    private final String teaType;
    private final Long batchCount;
    private final BigDecimal inputQtyKg;
    private final BigDecimal outputQtyKg;

    public TeaTypeYieldDTO(String teaType, Long batchCount, BigDecimal inputQtyKg, BigDecimal outputQtyKg) {
        this.teaType = teaType;
        this.batchCount = batchCount;
        this.inputQtyKg = inputQtyKg;
        this.outputQtyKg = outputQtyKg;
    }

    /** 产出率 % */
    public BigDecimal getYieldRatePct() {
        if (inputQtyKg == null || inputQtyKg.signum() == 0 || outputQtyKg == null) {
            return BigDecimal.ZERO;
        }
        return outputQtyKg.multiply(BigDecimal.valueOf(100)).divide(inputQtyKg, 2, RoundingMode.HALF_UP);
    }

    public String getTeaType() {
        return teaType;
    }

    public Long getBatchCount() {
        return batchCount;
    }

    public BigDecimal getInputQtyKg() {
        return inputQtyKg;
    }

    public BigDecimal getOutputQtyKg() {
        return outputQtyKg;
    }
}
