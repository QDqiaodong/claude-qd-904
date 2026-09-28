package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.domain.DryingRoom;
import com.tea.factory.domain.Equipment;
import com.tea.factory.repository.DryingRoomRepository;
import com.tea.factory.repository.EquipmentRepository;

/**
 * 设备与烘房：设备状态 / 温度维护，烘房在烘批次与温控曲线。
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final DryingRoomRepository dryingRoomRepository;

    public EquipmentService(EquipmentRepository equipmentRepository, DryingRoomRepository dryingRoomRepository) {
        this.equipmentRepository = equipmentRepository;
        this.dryingRoomRepository = dryingRoomRepository;
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
        if (!com.tea.factory.common.Dict.isValidEquipmentStatus(status)) {
            throw new BizException("设备状态不合法，可选：运行 / 待机 / 清洗 / 故障");
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
        return equipmentRepository.save(equipment);
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
