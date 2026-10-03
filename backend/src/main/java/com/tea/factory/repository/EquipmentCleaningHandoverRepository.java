package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.EquipmentCleaningHandover;

public interface EquipmentCleaningHandoverRepository extends JpaRepository<EquipmentCleaningHandover, Long> {

    Optional<EquipmentCleaningHandover> findByHandoverNo(String handoverNo);

    List<EquipmentCleaningHandover> findByEquipCodeOrderByIdDesc(String equipCode);

    List<EquipmentCleaningHandover> findTop50ByOrderByIdDesc();

    /**
     * 某设备当前进行中的交接单（清洗未确认 / 冻结 / 维修流程未结束）。
     * 一台设备同时至多一张，由清洗交接服务在事务内守卫。
     */
    List<EquipmentCleaningHandover> findByEquipCodeAndStatusInOrderByIdDesc(String equipCode, List<String> statuses);

    List<EquipmentCleaningHandover> findByStatusInOrderByIdDesc(List<String> statuses);
}
