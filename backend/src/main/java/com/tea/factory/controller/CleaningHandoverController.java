package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.EquipmentCleaningHandover;
import com.tea.factory.dto.HandoverActionRequest;
import com.tea.factory.dto.HandoverDetailDTO;
import com.tea.factory.service.CleaningHandoverService;

/**
 * 设备清洗交接台：
 * 工序收尾自动建单 → 清洗人开始/完成清洗或报障 → 维修 → 负责人确认恢复可用。
 */
@RestController
@RequestMapping("/api/handover")
public class CleaningHandoverController {

    private final CleaningHandoverService handoverService;

    public CleaningHandoverController(CleaningHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 交接单列表（可按状态过滤：PENDING/CLEANING/WAITING_CONFIRM/FAULT_FROZEN/REPAIR/REPAIRED/...） */
    @GetMapping
    public ApiResponse<List<EquipmentCleaningHandover>> list(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(handoverService.handoverList(status));
    }

    /** 交接详情：单据信息 + 操作留痕 + 冲突取舍说明 + 当前能否开工 */
    @GetMapping("/{handoverNo}")
    public ApiResponse<HandoverDetailDTO> detail(@PathVariable String handoverNo) {
        return ApiResponse.success(handoverService.detail(handoverNo));
    }

    /** 清洗人开始清洗 */
    @PostMapping("/{handoverNo}/start-clean")
    public ApiResponse<EquipmentCleaningHandover> startClean(@PathVariable String handoverNo,
            @RequestBody HandoverActionRequest request) {
        return ApiResponse.success("已开始清洗", handoverService.startClean(handoverNo, request));
    }

    /** 清洗人标记清洗完成（填残留风险、清洗方式、起止时间），进入等待确认 */
    @PostMapping("/{handoverNo}/finish-clean")
    public ApiResponse<EquipmentCleaningHandover> finishClean(@PathVariable String handoverNo,
            @RequestBody HandoverActionRequest request) {
        return ApiResponse.success("清洗已完成，等待负责人确认恢复可用",
                handoverService.finishClean(handoverNo, request));
    }

    /** 报故障：确认前立即冻结，确认后保留清洗转维修 */
    @PostMapping("/{handoverNo}/fault")
    public ApiResponse<EquipmentCleaningHandover> reportFault(@PathVariable String handoverNo,
            @RequestBody HandoverActionRequest request) {
        EquipmentCleaningHandover h = handoverService.reportFault(handoverNo, request);
        return ApiResponse.success("已报障，设备在负责人确认前不能开工", h);
    }

    /** 维修完成，等待负责人复检 */
    @PostMapping("/{handoverNo}/repair-done")
    public ApiResponse<EquipmentCleaningHandover> repairDone(@PathVariable String handoverNo,
            @RequestBody HandoverActionRequest request) {
        return ApiResponse.success("维修完成，等待负责人复检", handoverService.repairDone(handoverNo, request));
    }

    /** 负责人确认：清洗完成确认恢复可用 / 维修复检通过 */
    @PostMapping("/{handoverNo}/confirm")
    public ApiResponse<EquipmentCleaningHandover> confirm(@PathVariable String handoverNo,
            @RequestBody HandoverActionRequest request) {
        return ApiResponse.success("负责人已确认，设备恢复可用，可以接新批次",
                handoverService.confirm(handoverNo, request));
    }
}
