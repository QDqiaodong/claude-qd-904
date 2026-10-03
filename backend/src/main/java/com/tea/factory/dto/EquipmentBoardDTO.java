package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 设备看板行：设备原始状态 + 当前交接单共同推导出的“能否接新批次”。
 * available=false 时 blockedReason 给出人话原因，工序台与设备列表共用同一结论，避免互相矛盾。
 */
@Data
public class EquipmentBoardDTO {

    private Long id;
    private String equipCode;
    private String equipName;
    private String equipType;
    private String model;
    private BigDecimal capacityKg;
    private BigDecimal currentTempC;
    private String workshop;
    private String currentBatchNo;

    /** 设备原始状态 RUNNING / IDLE / CLEANING / FAULT */
    private String status;
    private String statusName;

    /** 当前生效交接单号（无则为空 —— 旧设备沿用原状态） */
    private String activeHandoverNo;

    /** 当前交接状态 PENDING / CLEANING / WAITING_CONFIRM / FAULT_FROZEN / REPAIR / REPAIRED ... */
    private String handoverStatus;
    private String handoverStatusName;

    /** 交接类型 CLEAN / REPAIR */
    private String handoverType;

    /** 是否可用于下一批次开工（唯一开工结论） */
    private boolean available;

    /** 不可开工原因（清洗未开始/清洗中/等待确认/故障冻结/维修中/待复检/设备故障/运行中/清洗中(旧状态)） */
    private String blockedReason;

    /** 是否为“没有历史交接单”的旧设备 */
    private boolean legacy;
}
