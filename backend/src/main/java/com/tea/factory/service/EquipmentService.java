package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.DryingRoom;
import com.tea.factory.domain.Equipment;
import com.tea.factory.domain.EquipmentCleaningHandover;
import com.tea.factory.dto.EquipmentBoardDTO;
import com.tea.factory.repository.DryingRoomRepository;
import com.tea.factory.repository.EquipmentCleaningHandoverRepository;
import com.tea.factory.repository.EquipmentRepository;

/**
 * 设备与烘房：设备状态 / 温度维护，烘房在烘批次与温控曲线。
 * 设备一旦存在进行中的清洗交接单，人工状态改“运行/待机”一律拦截——
 * 是否恢复可用只能由清洗交接流程（负责人确认）决定。
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final DryingRoomRepository dryingRoomRepository;
    private final EquipmentCleaningHandoverRepository handoverRepository;
    private final CleaningHandoverService cleaningHandoverService;

    public EquipmentService(EquipmentRepository equipmentRepository,
            DryingRoomRepository dryingRoomRepository,
            EquipmentCleaningHandoverRepository handoverRepository,
            CleaningHandoverService cleaningHandoverService) {
        this.equipmentRepository = equipmentRepository;
        this.dryingRoomRepository = dryingRoomRepository;
        this.handoverRepository = handoverRepository;
        this.cleaningHandoverService = cleaningHandoverService;
    }

    /** 设备看板（带清洗交接状态与唯一的开工可用性结论） */
    public List<EquipmentBoardDTO> board() {
        return cleaningHandoverService.board();
    }

    public List<Equipment> listEquipment() {
        return equipmentRepository.findAll();
    }

    public List<Equipment> listByType(String type) {
        return equipmentRepository.findByEquipTypeOrderByEquipCodeAsc(type);
    }

    @Transactional
    public Equipment updateStatus(String equipCode, String status, BigDecimal tempC) {
        Equipment equipment = equipmentRepository.findByEquipCode(equipCode);
        if (equipment == null) {
            throw new BizException("设备 " + equipCode + " 不存在");
        }
        if (!Dict.isValidEquipmentStatus(status)) {
            throw new BizException("设备状态不合法，可选：运行 / 待机 / 清洗 / 故障");
        }
        List<EquipmentCleaningHandover> actives = handoverRepository
                .findByEquipCodeAndStatusInOrderByIdDesc(equipCode, Dict.HANDOVER_ACTIVE_STATUSES);
        if (!actives.isEmpty() && ("IDLE".equals(status) || "RUNNING".equals(status))) {
            EquipmentCleaningHandover active = actives.get(0);
            throw new BizException("设备存在进行中的交接单 " + active.getHandoverNo() + "（"
                    + Dict.handoverStatusName(active.getStatus())
                    + "），不能手动改为待机/运行；请完成清洗交接并由负责人确认恢复可用");
        }
        if ("FAULT".equals(status) && "RUNNING".equals(equipment.getStatus())) {
            throw new BizException("设备正在运行，请先结束当前工序再报故障");
        }
        equipment.setStatus(status);
        if (tempC != null) {
            equipment.setCurrentTempC(tempC);
        }
        if ("RUNNING".equals(status)) {
            equipment.setLastMaintainAt(LocalDateTime.now());
        }
        try {
            return equipmentRepository.save(equipment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("设备状态刚刚被交接流程更新，请刷新后再操作");
        }
    }

    /** 无交接单设备直接报障（旧设备沿用原流程，也纳入交接台留痕） */
    @Transactional
    public Equipment reportFault(String equipCode, String operator, String faultNote) {
        EquipmentCleaningHandover repair = cleaningHandoverService
                .reportFaultByEquipment(equipCode, operator, faultNote);
        Equipment equipment = equipmentRepository.findByEquipCode(repair.getEquipCode());
        return equipment;
    }

    public List<DryingRoom> listRooms() {
        return dryingRoomRepository.findAll();
    }

    /** 烘房进烘：登记在烘批次与温控曲线 */
    @Transactional
    public DryingRoom load(String roomNo, String batchNo, String tempCurve, BigDecimal targetTempC,
            Integer dryingMinutes) {
        DryingRoom room = dryingRoomRepository.findByRoomNo(roomNo);
        if (room == null) {
            throw new BizException("烘房 " + roomNo + " 不存在");
        }
        if ("FAULT".equals(room.getStatus())) {
            throw new BizException("烘房 " + roomNo + " 处于故障状态，不能进烘");
        }
        if (room.getCurrentBatchNo() != null && !room.getCurrentBatchNo().isBlank()
                && !room.getCurrentBatchNo().equals(batchNo)) {
            throw new BizException("烘房 " + roomNo + " 正在烘批次 " + room.getCurrentBatchNo() + "，请先出烘");
        }
        room.setCurrentBatchNo(batchNo);
        room.setTempCurve(tempCurve);
        room.setTargetTempC(targetTempC);
        room.setCurrentTempC(targetTempC);
        room.setDryingMinutes(dryingMinutes);
        room.setStatus("RUNNING");
        room.setUpdatedAt(LocalDateTime.now());
        return dryingRoomRepository.save(room);
    }

    /** 出烘 */
    @Transactional
    public DryingRoom unload(String roomNo) {
        DryingRoom room = dryingRoomRepository.findByRoomNo(roomNo);
        if (room == null) {
            throw new BizException("烘房 " + roomNo + " 不存在");
        }
        room.setCurrentBatchNo(null);
        room.setStatus("IDLE");
        room.setUpdatedAt(LocalDateTime.now());
        return dryingRoomRepository.save(room);
    }
}
