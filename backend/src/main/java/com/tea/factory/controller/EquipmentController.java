package com.tea.factory.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.DryingRoom;
import com.tea.factory.domain.Equipment;
import com.tea.factory.dto.EquipmentBoardDTO;
import com.tea.factory.service.EquipmentService;

/**
 * 设备与烘房。
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /** 设备看板：原始状态 + 当前交接单 → 唯一的“能否开工”结论 */
    @GetMapping("/board")
    public ApiResponse<List<EquipmentBoardDTO>> board() {
        return ApiResponse.success(equipmentService.board());
    }

    @GetMapping
    public ApiResponse<List<Equipment>> list() {
        return ApiResponse.success(equipmentService.listEquipment());
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<Equipment>> listByType(@PathVariable String type) {
        return ApiResponse.success(equipmentService.listByType(type));
    }

    @PutMapping("/{equipCode}/status")
    public ApiResponse<Equipment> updateStatus(@PathVariable String equipCode,
            @RequestParam String status,
            @RequestParam(required = false) BigDecimal tempC) {
        return ApiResponse.success(equipmentService.updateStatus(equipCode, status, tempC));
    }

    /** 旧设备 / 待机设备直接报障（无交接单时使用，会自动开维修单并留痕） */
    @PostMapping("/{equipCode}/fault")
    public ApiResponse<Equipment> reportFault(@PathVariable String equipCode,
            @RequestBody FaultRequest request) {
        Equipment equipment = equipmentService.reportFault(equipCode, request.getOperator(), request.getFaultNote());
        return ApiResponse.success("设备 " + equipCode + " 已报障冻结，待维修与负责人复检", equipment);
    }

    @GetMapping("/rooms")
    public ApiResponse<List<DryingRoom>> rooms() {
        return ApiResponse.success(equipmentService.listRooms());
    }

    @PostMapping("/rooms/{roomNo}/load")
    public ApiResponse<DryingRoom> load(@PathVariable String roomNo,
            @RequestParam String batchNo,
            @RequestParam(required = false) String tempCurve,
            @RequestParam(required = false) BigDecimal targetTempC,
            @RequestParam(required = false) Integer dryingMinutes) {
        return ApiResponse.success("烘房 " + roomNo + " 已进烘批次 " + batchNo,
                equipmentService.load(roomNo, batchNo, tempCurve, targetTempC, dryingMinutes));
    }

    @PostMapping("/rooms/{roomNo}/unload")
    public ApiResponse<DryingRoom> unload(@PathVariable String roomNo) {
        return ApiResponse.success("烘房 " + roomNo + " 已出烘", equipmentService.unload(roomNo));
    }

    /** 直接报障请求体 */
    public static class FaultRequest {
        private String operator;
        private String faultNote;

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getFaultNote() {
            return faultNote;
        }

        public void setFaultNote(String faultNote) {
            this.faultNote = faultNote;
        }
    }
}
