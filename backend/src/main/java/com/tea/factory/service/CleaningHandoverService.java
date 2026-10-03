package com.tea.factory.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.Equipment;
import com.tea.factory.domain.EquipmentCleaningHandover;
import com.tea.factory.domain.HandoverActionLog;
import com.tea.factory.domain.ProcessStepRecord;
import com.tea.factory.generator.NoSequenceStore;
import com.tea.factory.repository.EquipmentCleaningHandoverRepository;
import com.tea.factory.repository.EquipmentRepository;
import com.tea.factory.repository.HandoverActionLogRepository;

/**
 * 设备清洗交接台：
 * 1) 工序收尾自动建交接单（带出批次 / 工序 / 设备），设备随即进入“未清洗完不得开工”的锁定；
 * 2) 清洗人开始 / 完成清洗（残留风险、清洗方式、起止时间），或直接报故障；
 * 3) 负责人确认恢复可用。
 *
 * 冲突取舍（由本服务确定并写入交接详情 impactNote）：
 *   · 负责人确认【之前】报障 → 立即冻结：清洗单置 FAULT_FROZEN、本次清洗作废，设备转维修，
 *     维修复检通过前一律不能开工；
 *   · 负责人确认【之后】报障 → 保留已确认的清洗结果，另开维修单（KEEP_CLEAN），
 *     设备转维修；维修复检通过即可复用，是否需要重洗由负责人在复检意见里决定。
 *
 * 并发：交接单与设备均带 @Version 乐观锁，所有动作先做状态机守卫再落库；
 * 多人几乎同时确认 / 报障时，只有一个结论生效，后到者收到“状态已变更”提示并刷新到先生效的状态。
 *
 * 旧设备：没有历史交接单时沿用设备原始状态（RUNNING/IDLE/FAULT/CLEANING），不被新流程统一锁死。
 */
@Service
public class CleaningHandoverService {

    public static final String HANDOVER_TABLE = "equipment_cleaning_handover";
    public static final String HANDOVER_COLUMN = "handover_no";

    private final EquipmentCleaningHandoverRepository handoverRepository;
    private final HandoverActionLogRepository logRepository;
    private final EquipmentRepository equipmentRepository;

    public CleaningHandoverService(EquipmentCleaningHandoverRepository handoverRepository,
            HandoverActionLogRepository logRepository,
            EquipmentRepository equipmentRepository) {
        this.handoverRepository = handoverRepository;
        this.logRepository = logRepository;
        this.equipmentRepository = equipmentRepository;
    }

    // ============================== 工序收尾建单 ==============================

    /**
     * 工序结束时在同一事务内调用：为刚用过的设备生成清洗交接单。
     * 没有用到设备的工序（如手工拣剔）不建单。
     */
    @Transactional
    public EquipmentCleaningHandover createOnFinishStep(Equipment equipment, ProcessStepRecord step) {
        if (equipment == null || step == null) {
            return null;
        }
        EquipmentCleaningHandover active = findActive(equipment.getEquipCode());
        if (active != null) {
            // 开工校验理论上已拦住，这里再兜底一次，避免一张设备挂两张进行中的交接单
            throw new BizException("设备 " + equipment.getEquipName() + " 已有进行中的清洗交接单 "
                    + active.getHandoverNo() + "（" + Dict.handoverStatusName(active.getStatus())
                    + "），完成并确认前不能再开新单");
        }

        LocalDateTime now = LocalDateTime.now();
        EquipmentCleaningHandover h = new EquipmentCleaningHandover();
        h.setHandoverNo(NoSequenceStore.next(HANDOVER_TABLE, HANDOVER_COLUMN, "QX"));
        h.setHandoverType(Dict.HANDOVER_CLEAN);
        h.setEquipCode(equipment.getEquipCode());
        h.setEquipName(equipment.getEquipName());
        h.setEquipType(equipment.getEquipType());
        h.setBatchNo(step.getBatchNo());
        h.setTeaType(step.getTeaType());
        h.setStepName(step.getStepName());
        h.setStepRecordId(step.getId());
        h.setStatus(Dict.HS_PENDING);
        h.setCreatedAt(now);
        h.setUpdatedAt(now);
        EquipmentCleaningHandover saved = handoverRepository.save(h);

        equipment.setStatus("CLEANING");
        equipment.setCurrentBatchNo(null);
        equipmentRepository.save(equipment);

        writeLog(saved, null, Dict.HS_PENDING, "CREATE", "系统（工序收尾）",
                "工序「" + Dict.stepName(step.getTeaType(), step.getStepName()) + "」收尾，自动生成清洗交接单，批次 "
                        + step.getBatchNo() + "；清洗未完成并经负责人确认前，本设备不得接新批次");
        return saved;
    }

    // ============================== 清洗流转 ==============================

    /** 清洗人开始清洗：登记清洗方式与起始时间 */
    @Transactional
    public EquipmentCleaningHandover startClean(String handoverNo, com.tea.factory.dto.HandoverActionRequest req) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        requireType(h, Dict.HANDOVER_CLEAN);
        requireOperator(req);
        guard(h, List.of(Dict.HS_PENDING), "开始清洗");

        if (req.getCleanMethod() != null && !req.getCleanMethod().isBlank()) {
            if (!Dict.isValidCleanMethod(req.getCleanMethod())) {
                throw new BizException("清洗方式不合法，可选：" + String.join("、", Dict.CLEAN_METHODS));
            }
            h.setCleanMethod(req.getCleanMethod());
        }
        if (h.getCleanMethod() == null) {
            throw new BizException("请先选择清洗方式再开始清洗");
        }
        if (req.getCleanNote() != null) {
            h.setCleanNote(req.getCleanNote());
        }
        LocalDateTime startedAt = req.getCleanStartedAt() != null ? req.getCleanStartedAt() : LocalDateTime.now();
        h.setCleanStartedAt(startedAt);
        h.setCleaner(req.getOperator());
        h.setStatus(Dict.HS_CLEANING);
        h.setUpdatedAt(LocalDateTime.now());
        EquipmentCleaningHandover saved = saveWithConcurrencyGuard(h);

        Equipment equipment = requireEquipment(h.getEquipCode());
        equipment.setStatus("CLEANING");
        equipmentRepository.save(equipment);

        writeLog(saved, Dict.HS_PENDING, Dict.HS_CLEANING, "START_CLEAN", req.getOperator(),
                "清洗开始，方式：" + h.getCleanMethod()
                        + (h.getCleanNote() != null && !h.getCleanNote().isBlank() ? "；" + h.getCleanNote() : ""));
        return saved;
    }

    /** 清洗人完成清洗：填写残留风险与结束时间，进入等待负责人确认 */
    @Transactional
    public EquipmentCleaningHandover finishClean(String handoverNo, com.tea.factory.dto.HandoverActionRequest req) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        requireType(h, Dict.HANDOVER_CLEAN);
        requireOperator(req);
        guard(h, List.of(Dict.HS_CLEANING), "标记清洗完成");

        if (req.getCleanMethod() != null && !req.getCleanMethod().isBlank()) {
            if (!Dict.isValidCleanMethod(req.getCleanMethod())) {
                throw new BizException("清洗方式不合法，可选：" + String.join("、", Dict.CLEAN_METHODS));
            }
            h.setCleanMethod(req.getCleanMethod());
        }
        if (h.getCleanMethod() == null) {
            throw new BizException("缺少清洗方式，无法完成清洗");
        }
        if (req.getResidueRisk() == null || !Dict.isValidResidueRisk(req.getResidueRisk())) {
            throw new BizException("请填写残留风险（低 / 中 / 高）后再标记完成");
        }
        h.setResidueRisk(req.getResidueRisk());
        if (req.getResidueNote() != null) {
            h.setResidueNote(req.getResidueNote());
        }
        if (req.getCleanNote() != null) {
            h.setCleanNote(req.getCleanNote());
        }
        LocalDateTime startedAt = h.getCleanStartedAt() != null
                ? h.getCleanStartedAt()
                : (req.getCleanStartedAt() != null ? req.getCleanStartedAt() : LocalDateTime.now());
        h.setCleanStartedAt(startedAt);
        LocalDateTime finishedAt = req.getCleanFinishedAt() != null ? req.getCleanFinishedAt() : LocalDateTime.now();
        if (finishedAt.isBefore(startedAt)) {
            throw new BizException("清洗结束时间不能早于开始时间");
        }
        h.setCleanFinishedAt(finishedAt);
        h.setCleaner(req.getOperator());
        h.setStatus(Dict.HS_WAITING_CONFIRM);
        h.setUpdatedAt(LocalDateTime.now());
        EquipmentCleaningHandover saved = saveWithConcurrencyGuard(h);

        Equipment equipment = requireEquipment(h.getEquipCode());
        equipment.setStatus("CLEANING");
        equipmentRepository.save(equipment);

        writeLog(saved, Dict.HS_CLEANING, Dict.HS_WAITING_CONFIRM, "FINISH_CLEAN", req.getOperator(),
                "清洗完成（" + h.getCleanMethod() + "），残留风险：" + h.getResidueRisk()
                        + "；等待负责人确认，确认前设备仍不可开工");
        return saved;
    }

    // ============================== 报障（含冲突取舍） ==============================

    /**
     * 交接单上报障：确认前 → 立即冻结并转维修；确认后 → 保留清洗结果转维修。
     */
    @Transactional
    public EquipmentCleaningHandover reportFault(String handoverNo, com.tea.factory.dto.HandoverActionRequest req) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        requireOperator(req);
        if (req.getFaultNote() == null || req.getFaultNote().isBlank()) {
            throw new BizException("请填写故障现象后再报障");
        }
        LocalDateTime now = LocalDateTime.now();

        Equipment equipment = requireEquipment(h.getEquipCode());
        if ("RUNNING".equals(equipment.getStatus())) {
            throw new BizException("设备正在加工批次，请先结束当前工序再报障");
        }

        switch (h.getStatus()) {
            // ---- 确认之前报障：立即冻结，本次清洗作废 ----
            case Dict.HS_PENDING, Dict.HS_CLEANING, Dict.HS_WAITING_CONFIRM -> {
                String fromStatus = h.getStatus();
                String freezeImpact = "报障发生在负责人确认恢复可用【之前】，按规定设备立即冻结：本次清洗"
                        + (Dict.HS_PENDING.equals(h.getStatus()) ? "尚未开始" : "未完成确认")
                        + "、结果作废，设备转维修；维修完成并经负责人复检确认前，一律不得用于下一批次开工。";
                h.setStatus(Dict.HS_FAULT_FROZEN);
                h.setResolution(Dict.RESOLUTION_FREEZE);
                h.setImpactNote(freezeImpact);
                h.setFaultNote(req.getFaultNote());
                h.setFaultAt(now);
                h.setFaultOperator(req.getOperator());
                h.setUpdatedAt(now);
                EquipmentCleaningHandover frozen = saveWithConcurrencyGuard(h);
                writeLog(frozen, fromStatus, Dict.HS_FAULT_FROZEN, "REPORT_FAULT", req.getOperator(),
                        "故障：" + req.getFaultNote() + "。" + freezeImpact);

                EquipmentCleaningHandover repair = openRepairOrder(frozen, equipment, req.getFaultNote(),
                        req.getOperator(), now, Dict.RESOLUTION_FREEZE,
                        "来源清洗单 " + frozen.getHandoverNo() + " 在确认前报障，已冻结并转维修");
                equipment.setStatus("FAULT");
                equipmentRepository.save(equipment);
                return repair;
            }
            // ---- 确认之后报障：保留已确认清洗结果，转维修 ----
            case Dict.HS_CONFIRMED, Dict.HS_AVAILABLE -> {
                String fromStatus = h.getStatus();
                String keepImpact = "报障发生在负责人确认恢复可用【之后】，保留已确认的清洗结果（不强制重洗），设备转维修；"
                        + "维修完成并经负责人复检确认前仍不得开工，是否需要补做清洗由负责人复检时判定。";
                EquipmentCleaningHandover repair = openRepairOrder(h, equipment, req.getFaultNote(),
                        req.getOperator(), now, Dict.RESOLUTION_KEEP_CLEAN, keepImpact);
                writeLog(h, fromStatus, fromStatus, "REPORT_FAULT", req.getOperator(),
                        "故障：" + req.getFaultNote() + "。清洗结论 " + h.getHandoverNo()
                                + " 已确认保留，另开维修单 " + repair.getHandoverNo());
                equipment.setStatus("FAULT");
                equipmentRepository.save(equipment);
                return repair;
            }
            // ---- 维修复检不合格：退回维修中，允许补充处理 ----
            case Dict.HS_REPAIRED -> {
                String fromStatus = Dict.HS_REPAIRED;
                h.setStatus(Dict.HS_REPAIR);
                h.setRepairNote(appendNote(h.getRepairNote(),
                        "复检不合格退回（" + req.getOperator() + "）：" + req.getFaultNote(), now));
                h.setUpdatedAt(now);
                EquipmentCleaningHandover reopened = saveWithConcurrencyGuard(h);
                writeLog(reopened, Dict.HS_REPAIRED, Dict.HS_REPAIR, "REPORT_FAULT", req.getOperator(),
                        "复检不合格，退回维修：" + req.getFaultNote());
                equipment.setStatus("FAULT");
                equipmentRepository.save(equipment);
                return reopened;
            }
            default -> throw new BizException("交接单 " + handoverNo + " 当前为「"
                    + Dict.handoverStatusName(h.getStatus()) + "」，已有生效处理结论，不能重复报障，请刷新查看最新状态");
        }
    }

    /**
     * 无交接单设备直接报障（旧设备 / 待机设备）：直接冻结并开维修单。
     */
    @Transactional
    public EquipmentCleaningHandover reportFaultByEquipment(String equipCode, String operator, String faultNote) {
        if (operator == null || operator.isBlank()) {
            throw new BizException("报障人不能为空");
        }
        if (faultNote == null || faultNote.isBlank()) {
            throw new BizException("请填写故障现象后再报障");
        }
        Equipment equipment = requireEquipment(equipCode);
        if ("RUNNING".equals(equipment.getStatus())) {
            throw new BizException("设备正在加工批次，请先结束当前工序再报障");
        }
        EquipmentCleaningHandover active = findActive(equipCode);
        if (active != null) {
            throw new BizException("设备已有进行中的交接单 " + active.getHandoverNo() + "（"
                    + Dict.handoverStatusName(active.getStatus()) + "），请在该单上报障");
        }
        LocalDateTime now = LocalDateTime.now();
        EquipmentCleaningHandover repair = openRepairOrder(null, equipment, faultNote, operator, now, null,
                "本设备无关联清洗交接单，直接报障冻结；维修完成并经负责人复检确认前，不得用于下一批次开工");
        equipment.setStatus("FAULT");
        equipmentRepository.save(equipment);
        return repair;
    }

    // ============================== 维修与复检 ==============================

    /** 维修完成：维修中 → 待负责人复检 */
    @Transactional
    public EquipmentCleaningHandover repairDone(String handoverNo, com.tea.factory.dto.HandoverActionRequest req) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        requireType(h, Dict.HANDOVER_REPAIR);
        requireOperator(req);
        guard(h, List.of(Dict.HS_REPAIR), "登记维修完成");
        if (req.getRepairNote() == null || req.getRepairNote().isBlank()) {
            throw new BizException("请填写维修处理说明");
        }
        h.setRepairNote(req.getRepairNote());
        h.setRepairer(req.getOperator());
        h.setRepairedAt(LocalDateTime.now());
        h.setStatus(Dict.HS_REPAIRED);
        h.setUpdatedAt(LocalDateTime.now());
        EquipmentCleaningHandover saved = saveWithConcurrencyGuard(h);

        Equipment equipment = requireEquipment(h.getEquipCode());
        equipment.setStatus("FAULT");
        equipmentRepository.save(equipment);

        writeLog(saved, Dict.HS_REPAIR, Dict.HS_REPAIRED, "REPAIR_DONE", req.getOperator(),
                "维修完成：" + req.getRepairNote() + "；等待负责人复检，复检通过前仍不可开工");
        return saved;
    }

    /**
     * 负责人确认：
     *   清洗单 WAITING_CONFIRM → CONFIRMED（恢复可用）；
     *   维修单 REPAIRED → AVAILABLE（复检通过恢复可用）。
     */
    @Transactional
    public EquipmentCleaningHandover confirm(String handoverNo, com.tea.factory.dto.HandoverActionRequest req) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        requireOperator(req);
        if (req.getConfirmNote() == null || req.getConfirmNote().isBlank()) {
            throw new BizException("请填写负责人确认意见");
        }
        LocalDateTime now = LocalDateTime.now();
        String action;
        String fromStatus = h.getStatus();

        if (Dict.HANDOVER_CLEAN.equals(h.getHandoverType())) {
            guard(h, List.of(Dict.HS_WAITING_CONFIRM), "确认恢复可用");
            h.setStatus(Dict.HS_CONFIRMED);
            action = "CONFIRM_CLEAN";
        } else {
            guard(h, List.of(Dict.HS_REPAIRED), "复检确认恢复可用");
            h.setStatus(Dict.HS_AVAILABLE);
            action = "CONFIRM_REPAIR";
        }
        h.setConfirmer(req.getOperator());
        h.setConfirmNote(req.getConfirmNote());
        h.setConfirmedAt(now);
        h.setUpdatedAt(now);
        EquipmentCleaningHandover saved = saveWithConcurrencyGuard(h);

        Equipment equipment = requireEquipment(h.getEquipCode());
        equipment.setStatus("IDLE");
        equipmentRepository.save(equipment);

        writeLog(saved, fromStatus, h.getStatus(), action, req.getOperator(),
                "负责人确认：" + req.getConfirmNote() + "；设备恢复可用，可接下一批次");
        return saved;
    }

    // ============================== 查询：看板 / 详情 ==============================

    /** 设备看板：开工可用性只由这里推导，工序台与交接台共用 */
    @Transactional(readOnly = true)
    public List<com.tea.factory.dto.EquipmentBoardDTO> board() {
        List<Equipment> equipments = equipmentRepository.findAll();
        Map<String, List<EquipmentCleaningHandover>> byEquip = new HashMap<>();
        for (EquipmentCleaningHandover h : handoverRepository.findAll()) {
            byEquip.computeIfAbsent(h.getEquipCode(), k -> new ArrayList<>()).add(h);
        }
        List<com.tea.factory.dto.EquipmentBoardDTO> result = new ArrayList<>();
        for (Equipment equipment : equipments) {
            result.add(toBoard(equipment, byEquip.get(equipment.getEquipCode())));
        }
        result.sort(Comparator.comparing(com.tea.factory.dto.EquipmentBoardDTO::getEquipCode));
        return result;
    }

    /**
     * 开工校验的唯一口径：清洗未开始 / 清洗中 / 等待确认 / 故障冻结 / 维修中 / 待复检 一律拦截；
     * 没有历史交接单的旧设备按原始状态判定（IDLE 放行，RUNNING/FAULT/CLEANING 拦截）。
     */
    @Transactional(readOnly = true)
    public void assertCanStartBatch(String equipCode) {
        Equipment equipment = equipmentRepository.findByEquipCode(equipCode);
        if (equipment == null) {
            throw new BizException("设备 " + equipCode + " 不存在");
        }
        EquipmentCleaningHandover active = findActive(equipCode);
        String reason = blockedReason(equipment, active);
        if (reason != null) {
            String who = active != null ? "（交接单 " + active.getHandoverNo() + "）" : "";
            throw new BizException("设备 " + (equipment.getEquipName() == null ? equipCode : equipment.getEquipName())
                    + who + " " + reason + "，不能用于下一批次开工");
        }
    }

    @Transactional(readOnly = true)
    public List<EquipmentCleaningHandover> handoverList(String status) {
        if (status != null && !status.isBlank()) {
            return handoverRepository.findByStatusInOrderByIdDesc(List.of(status.toUpperCase()));
        }
        return handoverRepository.findTop50ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public com.tea.factory.dto.HandoverDetailDTO detail(String handoverNo) {
        EquipmentCleaningHandover h = requireHandover(handoverNo);
        com.tea.factory.dto.HandoverDetailDTO dto = new com.tea.factory.dto.HandoverDetailDTO();
        dto.setHandover(h);
        dto.setLogs(logRepository.findByHandoverIdOrderByIdAsc(h.getId()));
        dto.setStatusName(Dict.handoverStatusName(h.getStatus()));
        dto.setTeaTypeName(h.getTeaType() == null ? null : Dict.teaTypeName(h.getTeaType()));
        dto.setStepName(h.getStepName() == null ? null
                : Dict.stepName(h.getTeaType(), h.getStepName()));
        dto.setResolutionName(Dict.resolutionName(h.getResolution()));

        Equipment equipment = equipmentRepository.findByEquipCode(h.getEquipCode());
        if (equipment != null) {
            dto.setEquipmentStatus(equipment.getStatus());
            dto.setEquipmentStatusName(Dict.equipmentStatusName(equipment.getStatus()));
            EquipmentCleaningHandover active = findActive(h.getEquipCode());
            String reason = blockedReason(equipment, active);
            dto.setEquipmentAvailable(reason == null);
            dto.setBlockedReason(reason);
        } else {
            dto.setEquipmentAvailable(false);
            dto.setBlockedReason("设备档案不存在");
        }
        return dto;
    }

    // ============================== 内部方法 ==============================

    /** 新开维修单（确认前冻结 / 确认后报障 / 直接报障共用） */
    private EquipmentCleaningHandover openRepairOrder(EquipmentCleaningHandover source, Equipment equipment,
            String faultNote, String operator, LocalDateTime now, String resolution, String impactNote) {
        EquipmentCleaningHandover repair = new EquipmentCleaningHandover();
        repair.setHandoverNo(NoSequenceStore.next(HANDOVER_TABLE, HANDOVER_COLUMN, "RX"));
        repair.setHandoverType(Dict.HANDOVER_REPAIR);
        repair.setEquipCode(equipment.getEquipCode());
        repair.setEquipName(equipment.getEquipName());
        repair.setEquipType(equipment.getEquipType());
        repair.setStatus(Dict.HS_REPAIR);
        repair.setFaultNote(faultNote);
        repair.setFaultAt(now);
        repair.setFaultOperator(operator);
        repair.setResolution(resolution);
        repair.setImpactNote(impactNote);
        if (source != null) {
            repair.setSourceHandoverNo(source.getHandoverNo());
            if (Dict.HANDOVER_CLEAN.equals(source.getHandoverType())) {
                repair.setBatchNo(source.getBatchNo());
                repair.setTeaType(source.getTeaType());
                repair.setStepName(source.getStepName());
                repair.setStepRecordId(source.getStepRecordId());
            }
        }
        repair.setCreatedAt(now);
        repair.setUpdatedAt(now);
        EquipmentCleaningHandover saved = handoverRepository.save(repair);
        writeLog(saved,
                source != null && Dict.HS_CONFIRMED.equals(source.getStatus()) ? Dict.HS_CONFIRMED
                        : source != null && Dict.HS_AVAILABLE.equals(source.getStatus()) ? Dict.HS_AVAILABLE : null,
                Dict.HS_REPAIR, "REPORT_FAULT", operator, "故障：" + faultNote + "。" + impactNote);
        return saved;
    }

    /**
     * 返回设备不能开工的原因；返回 null 表示可开工。这是全系统唯一的开工判定口径。
     */
    private String blockedReason(Equipment equipment, EquipmentCleaningHandover active) {
        if (active != null) {
            return switch (active.getStatus()) {
                case Dict.HS_PENDING -> "清洗交接单已生成但清洗尚未开始";
                case Dict.HS_CLEANING -> "正在清洗中";
                case Dict.HS_WAITING_CONFIRM -> "清洗已完成，尚待负责人确认恢复可用";
                case Dict.HS_FAULT_FROZEN -> "已报障冻结，等待维修";
                case Dict.HS_REPAIR -> "正在维修中";
                case Dict.HS_REPAIRED -> "维修已完成，尚待负责人复检";
                default -> "交接流程未结束";
            };
        }
        // 旧设备：没有进行中的交接单，沿用原始状态，不做新流程锁定
        return switch (equipment.getStatus()) {
            case "FAULT" -> "设备处于故障状态";
            case "RUNNING" -> "设备正在加工其他批次";
            case "CLEANING" -> "设备处于清洗状态（旧状态未建交接单，请在设备台核实后恢复）";
            default -> null;
        };
    }

    private com.tea.factory.dto.EquipmentBoardDTO toBoard(Equipment equipment,
            List<EquipmentCleaningHandover> handovers) {
        com.tea.factory.dto.EquipmentBoardDTO dto = new com.tea.factory.dto.EquipmentBoardDTO();
        dto.setId(equipment.getId());
        dto.setEquipCode(equipment.getEquipCode());
        dto.setEquipName(equipment.getEquipName());
        dto.setEquipType(equipment.getEquipType());
        dto.setModel(equipment.getModel());
        dto.setCapacityKg(equipment.getCapacityKg());
        dto.setCurrentTempC(equipment.getCurrentTempC());
        dto.setWorkshop(equipment.getWorkshop());
        dto.setCurrentBatchNo(equipment.getCurrentBatchNo());
        dto.setStatus(equipment.getStatus());
        dto.setStatusName(Dict.equipmentStatusName(equipment.getStatus()));
        dto.setLegacy(handovers == null || handovers.isEmpty());

        EquipmentCleaningHandover active = null;
        if (handovers != null) {
            active = handovers.stream()
                    .filter(h -> Dict.HANDOVER_ACTIVE_STATUSES.contains(h.getStatus()))
                    .max(Comparator.comparing(EquipmentCleaningHandover::getId))
                    .orElse(null);
        }
        if (active != null) {
            dto.setActiveHandoverNo(active.getHandoverNo());
            dto.setHandoverStatus(active.getStatus());
            dto.setHandoverStatusName(Dict.handoverStatusName(active.getStatus()));
            dto.setHandoverType(active.getHandoverType());
        }
        String reason = blockedReason(equipment, active);
        dto.setAvailable(reason == null);
        dto.setBlockedReason(reason);
        return dto;
    }

    /** 同设备进行中的交接单（取最新一张） */
    private EquipmentCleaningHandover findActive(String equipCode) {
        List<EquipmentCleaningHandover> actives = handoverRepository
                .findByEquipCodeAndStatusInOrderByIdDesc(equipCode, Dict.HANDOVER_ACTIVE_STATUSES);
        return actives.isEmpty() ? null : actives.get(0);
    }

    private void guard(EquipmentCleaningHandover h, List<String> allowed, String actionName) {
        if (!allowed.contains(h.getStatus())) {
            throw new BizException("交接单 " + h.getHandoverNo() + " 当前状态为「"
                    + Dict.handoverStatusName(h.getStatus()) + "」，不能执行「" + actionName
                    + "」；可能已有同事先行操作，请刷新查看已生效的状态");
        }
    }

    private void requireType(EquipmentCleaningHandover h, String type) {
        if (!type.equals(h.getHandoverType())) {
            throw new BizException("交接单 " + h.getHandoverNo() + " 是"
                    + (Dict.HANDOVER_REPAIR.equals(h.getHandoverType()) ? "维修单" : "清洗单")
                    + "，不能执行该操作");
        }
    }

    private void requireOperator(com.tea.factory.dto.HandoverActionRequest req) {
        if (req == null || req.getOperator() == null || req.getOperator().isBlank()) {
            throw new BizException("操作人不能为空");
        }
    }

    private EquipmentCleaningHandover requireHandover(String handoverNo) {
        return handoverRepository.findByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
    }

    private Equipment requireEquipment(String equipCode) {
        Equipment equipment = equipmentRepository.findByEquipCode(equipCode);
        if (equipment == null) {
            throw new BizException("设备 " + equipCode + " 不存在");
        }
        return equipment;
    }

    /** 乐观锁冲突统一翻译成人话：后到者看到的是“先前已生效”的结论 */
    private EquipmentCleaningHandover saveWithConcurrencyGuard(EquipmentCleaningHandover h) {
        try {
            return handoverRepository.save(h);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("交接单 " + h.getHandoverNo()
                    + " 已被其他同事先行操作并生效，当前结论保留先到者；请刷新页面查看最新状态后再操作");
        }
    }

    private void writeLog(EquipmentCleaningHandover h, String fromStatus, String toStatus,
            String action, String operator, String detail) {
        HandoverActionLog log = new HandoverActionLog();
        log.setHandoverId(h.getId());
        log.setHandoverNo(h.getHandoverNo());
        log.setEquipCode(h.getEquipCode());
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperator(operator);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    private String appendNote(String oldNote, String addition, LocalDateTime now) {
        String stamp = now.toLocalDate().toString();
        if (oldNote == null || oldNote.isBlank()) {
            return "[" + stamp + "] " + addition;
        }
        return oldNote + " | [" + stamp + "] " + addition;
    }
}
