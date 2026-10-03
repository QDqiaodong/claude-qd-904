package com.tea.factory.dto;

import java.util.List;

import com.tea.factory.domain.EquipmentCleaningHandover;
import com.tea.factory.domain.HandoverActionLog;

import lombok.Data;

/**
 * 交接详情：交接单本体 + 操作留痕时间线 + 设备当前是否可开工的结论。
 * impactNote 必须向清洗/负责人说明本次“清洗完成 vs 报故障”的取舍及其对开工的影响。
 */
@Data
public class HandoverDetailDTO {

    private EquipmentCleaningHandover handover;

    private List<HandoverActionLog> logs;

    /** 设备当前是否可开工（与设备列表、工序台同口径） */
    private boolean equipmentAvailable;

    private String blockedReason;

    /** 设备当前原始状态 */
    private String equipmentStatus;
    private String equipmentStatusName;

    private String statusName;

    private String teaTypeName;

    private String stepName;

    /** 冲突取舍中文名：立即冻结 / 保留清洗转维修 */
    private String resolutionName;
}
