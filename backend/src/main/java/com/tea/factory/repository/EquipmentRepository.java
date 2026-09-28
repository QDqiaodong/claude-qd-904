package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Equipment findByEquipCode(String equipCode);

    List<Equipment> findByEquipTypeOrderByEquipCodeAsc(String equipType);

    List<Equipment> findByStatusOrderByEquipCodeAsc(String status);
}
