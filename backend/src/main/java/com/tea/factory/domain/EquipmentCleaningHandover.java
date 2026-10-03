package com.tea.factory.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 设备清洗交接单：工序收尾自动生成，带出刚完成的批次、工序与设备。
 * 流转：PENDING（未开始）→ CLEANING（清洗中）→ WAITING_CONFIRM（等确认）→ CONFIRMED（恢复可用）。
 * 报障分支：确认前报障 FAULT_FROZEN（立即冻结，清洗作废）；确认后报障另开 REPAIR 单（保留清洗结论）。
 * 维修分支：REPAIR（维修中）→ REPAIRED（待复检）→ AVAILABLE（复检通过恢复可用）。
 */
@Data
@Entity
@Table(name = "equipment_cleaning_handover")
public class EquipmentCleaningHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 交接单号：清洗 QX+日期+序号，维修 RX+日期+序号 */
    @Column(name = "handover_no", nullable = false, length = 40, unique = true)
    private String handoverNo;

    /** CLEAN 清洗交接 / REPAIR 维修交接 */
    @Column(name = "handover_type", nullable = false, length = 16)
    private String handoverType;

    @Column(name = "equip_code", nullable = false, length = 32)
    private String equipCode;

    @Column(name = "equip_name", length = 64)
    private String equipName;

    @Column(name = "equip_type", length = 16)
    private String equipType;

    /** 来源批次号（工序收尾带出，可空） */
    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 来源工序键 SPREAD / FIX / ROLL / ... */
    @Column(name = "step_name", length = 16)
    private String stepName;

    @Column(name = "step_record_id")
    private Long stepRecordId;

    /** 维修单对应的来源清洗单号 */
    @Column(name = "source_handover_no", length = 40)
    private String sourceHandoverNo;

    /** PENDING / CLEANING / WAITING_CONFIRM / CONFIRMED / FAULT_FROZEN / REPAIR / REPAIRED / AVAILABLE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 残留风险：低 / 中 / 高 */
    @Column(name = "residue_risk", length = 16)
    private String residueRisk;

    @Column(name = "residue_note", length = 255)
    private String residueNote;

    /** 清洗方式：高压水冲洗 / 蒸汽清洗 / 人工拆洗 / 清洗剂循环 / 干法清扫 */
    @Column(name = "clean_method", length = 32)
    private String cleanMethod;

    @Column(name = "clean_note", length = 255)
    private String cleanNote;

    @Column(name = "clean_started_at")
    private LocalDateTime cleanStartedAt;

    @Column(name = "clean_finished_at")
    private LocalDateTime cleanFinishedAt;

    @Column(name = "cleaner", length = 32)
    private String cleaner;

    @Column(name = "fault_note", length = 255)
    private String faultNote;

    @Column(name = "fault_at")
    private LocalDateTime faultAt;

    @Column(name = "fault_operator", length = 32)
    private String faultOperator;

    @Column(name = "repair_note", length = 255)
    private String repairNote;

    @Column(name = "repaired_at")
    private LocalDateTime repairedAt;

    @Column(name = "repairer", length = 32)
    private String repairer;

    @Column(name = "confirm_note", length = 255)
    private String confirmNote;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmer", length = 32)
    private String confirmer;

    /** 冲突取舍：FREEZE 立即冻结 / KEEP_CLEAN 保留清洗转维修 */
    @Column(name = "resolution", length = 16)
    private String resolution;

    /** 本次取舍及其对开工影响的说明（交接详情必须展示） */
    @Column(name = "impact_note", length = 512)
    private String impactNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 乐观锁：多人几乎同时确认 / 报障时只保留第一个生效结论 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
