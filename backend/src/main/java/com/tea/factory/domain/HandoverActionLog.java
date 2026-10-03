package com.tea.factory.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 清洗交接操作留痕：建单、开始 / 完成清洗、报障、维修完成、负责人确认等每一步都落一条，
 * 保证责任可追溯，后到者也能从时间线上看到先前已生效的结论。
 */
@Data
@Entity
@Table(name = "handover_action_log")
public class HandoverActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(name = "handover_no", nullable = false, length = 40)
    private String handoverNo;

    @Column(name = "equip_code", nullable = false, length = 32)
    private String equipCode;

    /** CREATE / START_CLEAN / FINISH_CLEAN / REPORT_FAULT / REPAIR_DONE / CONFIRM_CLEAN / CONFIRM_REPAIR */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
