package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.HandoverActionLog;

public interface HandoverActionLogRepository extends JpaRepository<HandoverActionLog, Long> {

    List<HandoverActionLog> findByHandoverIdOrderByIdAsc(Long handoverId);

    List<HandoverActionLog> findByEquipCodeOrderByIdDesc(String equipCode);
}
