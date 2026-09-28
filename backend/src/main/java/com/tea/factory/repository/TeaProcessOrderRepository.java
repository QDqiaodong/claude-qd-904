package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.TeaTypeYieldDTO;

public interface TeaProcessOrderRepository extends JpaRepository<TeaProcessOrder, String> {

    List<TeaProcessOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<TeaProcessOrder> findTop20ByOrderByCreatedAtDesc();

    /**
     * 手写 JPQL：按茶类统计投料量、产出量与批次数量（产出率由 DTO 计算）。
     */
    @Query("select new com.tea.factory.dto.TeaTypeYieldDTO("
            + "o.teaType, count(o), sum(o.inputQtyKg), sum(o.outputQtyKg)) "
            + "from TeaProcessOrder o "
            + "group by o.teaType")
    List<TeaTypeYieldDTO> summarizeYieldByTeaType();

    /**
     * 手写 JPQL：各茶类当前停留工序分布。
     */
    @Query("select o.teaType, o.currentStep, count(o) "
            + "from TeaProcessOrder o where o.status = 'PROCESSING' "
            + "group by o.teaType, o.currentStep")
    List<Object[]> summarizeCurrentStep();
}
