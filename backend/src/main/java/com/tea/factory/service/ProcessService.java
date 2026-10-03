package com.tea.factory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.Equipment;
import com.tea.factory.domain.EquipmentCleaningHandover;
import com.tea.factory.domain.ProcessStepRecord;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.OrderCreateRequest;
import com.tea.factory.dto.OrderDetailDTO;
import com.tea.factory.dto.StepRequest;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.repository.EquipmentRepository;
import com.tea.factory.repository.ProcessStepRecordRepository;
import com.tea.factory.repository.TeaProcessOrderRepository;

/**
 * 制茶工序流转：工单按茶类的工序序列推进，不能跳步；
 * 杀青温度、发酵 / 渥堆时长不达标不允许结束本工序。
 */
@Service
public class ProcessService {

    private final TeaProcessOrderRepository orderRepository;
    private final ProcessStepRecordRepository stepRepository;
    private final EquipmentRepository equipmentRepository;
    private final CleaningHandoverService cleaningHandoverService;

    public ProcessService(TeaProcessOrderRepository orderRepository,
            ProcessStepRecordRepository stepRepository,
            EquipmentRepository equipmentRepository,
            CleaningHandoverService cleaningHandoverService) {
        this.orderRepository = orderRepository;
        this.stepRepository = stepRepository;
        this.equipmentRepository = equipmentRepository;
        this.cleaningHandoverService = cleaningHandoverService;
    }

    /** 新建工单，批次号由 Hibernate 自定义生成器生成 */
    @Transactional
    public TeaProcessOrder createOrder(OrderCreateRequest request) {
        if (!Dict.isValidTeaType(request.getTeaType())) {
            throw new BizException("茶类不合法，可选：" + String.join("、", Dict.TEA_TYPES));
        }
        List<String> flow = Dict.flow(request.getTeaType());
        TeaProcessOrder order = new TeaProcessOrder();
        order.setTeaType(request.getTeaType());
        order.setProcessSeq(String.join(">", flow));
        order.setCurrentStep(flow.get(0));
        order.setCurrentStepIndex(0);
        order.setInputQtyKg(request.getInputQtyKg());
        order.setOutputQtyKg(BigDecimal.ZERO);
        order.setSourceTroughNo(request.getSourceTroughNo());
        order.setOperator(request.getOperator() == null ? "周师傅" : request.getOperator());
        order.setStatus("PROCESSING");
        order.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<OrderDetailDTO> orderList() {
        List<OrderDetailDTO> result = new ArrayList<>();
        for (TeaProcessOrder order : orderRepository.findTop20ByOrderByCreatedAtDesc()) {
            result.add(toDetail(order));
        }
        return result;
    }

    public OrderDetailDTO orderDetail(String batchNo) {
        TeaProcessOrder order = orderRepository.findById(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        return toDetail(order);
    }

    /**
     * 开始某道工序：必须正好是当前工序，不能跳步。
     */
    @Transactional
    public ProcessStepRecord startStep(String batchNo, StepRequest request) {
        TeaProcessOrder order = requireOrder(batchNo);
        List<String> flow = Dict.flow(order.getTeaType());
        String step = requiredStep(request.getStepName());
        int index = flow.indexOf(step);
        if (index < 0) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」不属于"
                    + Dict.teaTypeName(order.getTeaType()) + "的工序序列：" + joinFlow(order.getTeaType(), flow));
        }
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("批次 " + batchNo + " 已经完成全部工序，不能再开工");
        }
        if (index > order.getCurrentStepIndex()) {
            throw new BizException("工序不能跳步，当前应先做「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        if (index < order.getCurrentStepIndex()) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」已经做过了，当前工序是「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        Optional<ProcessStepRecord> running = stepRepository.findByBatchNoAndStepNameAndStatus(batchNo, step, "RUNNING");
        if (running.isPresent()) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」正在执行中，请先结束本工序");
        }

        ProcessStepRecord record = new ProcessStepRecord();
        record.setBatchNo(batchNo);
        record.setTeaType(order.getTeaType());
        record.setStepName(step);
        record.setStepIndex(index);
        record.setEquipmentCode(request.getEquipmentCode());
        record.setStartTime(LocalDateTime.now());
        record.setParamTempC(request.getParamTempC());
        record.setParamTemp2C(request.getParamTemp2C());
        record.setParamMinutes(request.getParamMinutes());
        record.setParamMinutes2(request.getParamMinutes2());
        record.setParamRpm(request.getParamRpm());
        record.setParamPressure(request.getParamPressure());
        record.setParamLeafQty(request.getParamLeafQty() == null ? order.getInputQtyKg() : request.getParamLeafQty());
        record.setHumidityPct(request.getHumidityPct());
        record.setParamProfile(request.getParamProfile());
        record.setOperator(request.getOperator() == null ? order.getOperator() : request.getOperator());
        record.setAbnormalNote(request.getAbnormalNote());
        record.setStatus("RUNNING");

        if (request.getEquipmentCode() != null && !request.getEquipmentCode().isBlank()) {
            Equipment equipment = equipmentRepository.findByEquipCode(request.getEquipmentCode());
            if (equipment == null) {
                throw new BizException("设备 " + request.getEquipmentCode() + " 不存在");
            }
            // 开工唯一校验：清洗未开始/进行中/等待确认/故障冻结/维修中等一律拦截；
            // 无交接单的旧设备沿用原状态。结论与设备看板完全同源。
            cleaningHandoverService.assertCanStartBatch(request.getEquipmentCode());
            equipment.setStatus("RUNNING");
            equipment.setCurrentBatchNo(batchNo);
            if (request.getParamTempC() != null) {
                equipment.setCurrentTempC(request.getParamTempC());
            }
            equipmentRepository.save(equipment);
        }
        return stepRepository.save(record);
    }

    /**
     * 结束本工序：校验工艺参数后自动推进到下一工序；用了设备的同时生成清洗交接单。
     */
    @Transactional
    public FinishStepResult finishStep(String batchNo, StepRequest request) {
        TeaProcessOrder order = requireOrder(batchNo);
        List<String> flow = Dict.flow(order.getTeaType());
        String step = requiredStep(request.getStepName());
        int index = flow.indexOf(step);
        if (index != order.getCurrentStepIndex()) {
            throw new BizException("只能结束当前工序「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        ProcessStepRecord record = stepRepository.findByBatchNoAndStepNameAndStatus(batchNo, step, "RUNNING")
                .orElseThrow(() -> new BizException("「" + Dict.stepName(order.getTeaType(), step)
                        + "」还没有开始，请先点「开始本工序」"));

        // 参数回填
        if (request.getParamTempC() != null) {
            record.setParamTempC(request.getParamTempC());
        }
        if (request.getParamTemp2C() != null) {
            record.setParamTemp2C(request.getParamTemp2C());
        }
        if (request.getParamMinutes() != null) {
            record.setParamMinutes(request.getParamMinutes());
        }
        if (request.getParamMinutes2() != null) {
            record.setParamMinutes2(request.getParamMinutes2());
        }
        if (request.getParamRpm() != null) {
            record.setParamRpm(request.getParamRpm());
        }
        if (request.getParamPressure() != null) {
            record.setParamPressure(request.getParamPressure());
        }
        if (request.getParamLeafQty() != null) {
            record.setParamLeafQty(request.getParamLeafQty());
        }
        if (request.getHumidityPct() != null) {
            record.setHumidityPct(request.getHumidityPct());
        }
        if (request.getParamProfile() != null && !request.getParamProfile().isBlank()) {
            record.setParamProfile(request.getParamProfile());
        }
        if (request.getAbnormalNote() != null && !request.getAbnormalNote().isBlank()) {
            record.setAbnormalNote(request.getAbnormalNote());
        }

        // ---- 工艺规则校验 ----
        validateStepParams(order, step, record);

        record.setEndTime(LocalDateTime.now());
        record.setStatus("DONE");
        stepRepository.save(record);

        String handoverNo = null;
        if (record.getEquipmentCode() != null && !record.getEquipmentCode().isBlank()) {
            Equipment equipment = equipmentRepository.findByEquipCode(record.getEquipmentCode());
            if (equipment != null) {
                // 工序收尾：生成清洗交接单（带出刚完成的批次与设备），设备进入“未清洗完不得开工”锁定
                EquipmentCleaningHandover handover = cleaningHandoverService.createOnFinishStep(equipment, record);
                handoverNo = handover == null ? null : handover.getHandoverNo();
            }
        }

        int next = index + 1;
        if (next >= flow.size()) {
            order.setStatus("DONE");
            order.setCurrentStep("DONE");
            order.setCurrentStepIndex(next);
            order.setOutputQtyKg(order.getInputQtyKg().multiply(Dict.yieldRate(order.getTeaType()))
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            order.setCurrentStep(flow.get(next));
            order.setCurrentStepIndex(next);
        }
        orderRepository.save(order);
        return new FinishStepResult(toDetail(order), handoverNo);
    }

    /** 工序参数校验 */
    private void validateStepParams(TeaProcessOrder order, String step, ProcessStepRecord record) {
        if (Dict.STEP_FIX.equals(step)) {
            BigDecimal min = Dict.minFixTemp(order.getTeaType());
            BigDecimal temp = record.getParamTempC();
            if (temp == null) {
                throw new BizException("杀青必须记录温度");
            }
            if (temp.compareTo(min) < 0) {
                throw new BizException(Dict.teaTypeName(order.getTeaType()) + "杀青温度不得低于 " + min
                        + "℃，当前仅 " + temp + "℃，不允许进入下一工序");
            }
        }
        if (Dict.STEP_FERMENT.equals(step)) {
            int min = Dict.minFermentMinutes(order.getTeaType());
            Integer minutes = record.getParamMinutes();
            if (minutes == null) {
                throw new BizException("发酵 / 渥堆必须记录时长");
            }
            if (minutes < min) {
                throw new BizException(Dict.stepName(order.getTeaType(), step) + "时长不得少于 " + min
                        + " 分钟，当前仅 " + minutes + " 分钟，不允许进入干燥");
            }
        }
        if (Dict.STEP_ROLL.equals(step) && record.getParamRpm() == null) {
            throw new BizException("揉捻必须记录转速");
        }
        if (Dict.STEP_DRY.equals(step) && record.getParamTempC() == null) {
            throw new BizException("干燥必须记录毛火温度");
        }
    }

    public List<TeaTypeYieldDTO> yieldByTeaType() {
        return orderRepository.summarizeYieldByTeaType();
    }

    public List<ProcessStepRecord> stepsOf(String batchNo) {
        return stepRepository.findByBatchNoOrderByStepIndexAsc(batchNo);
    }

    private TeaProcessOrder requireOrder(String batchNo) {
        return orderRepository.findById(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
    }

    private String requiredStep(String step) {
        if (step == null || step.isBlank()) {
            throw new BizException("工序名不能为空");
        }
        return step;
    }

    private String joinFlow(String teaType, List<String> flow) {
        List<String> names = new ArrayList<>();
        for (String item : flow) {
            names.add(Dict.stepName(teaType, item));
        }
        return String.join("→", names);
    }

    private OrderDetailDTO toDetail(TeaProcessOrder order) {        List<String> flow = Dict.flow(order.getTeaType());
        OrderDetailDTO detail = new OrderDetailDTO();
        detail.setBatchNo(order.getBatchNo());
        detail.setTeaType(order.getTeaType());
        detail.setTeaTypeName(Dict.teaTypeName(order.getTeaType()));
        detail.setFlow(flow);
        List<String> names = new ArrayList<>();
        for (String item : flow) {
            names.add(Dict.stepName(order.getTeaType(), item));
        }
        detail.setFlowNames(names);
        detail.setCurrentStep(order.getCurrentStep());
        detail.setCurrentStepName("DONE".equals(order.getCurrentStep())
                ? "已完成"
                : Dict.stepName(order.getTeaType(), order.getCurrentStep()));
        detail.setCurrentStepIndex(order.getCurrentStepIndex());
        detail.setInputQtyKg(order.getInputQtyKg());
        detail.setOutputQtyKg(order.getOutputQtyKg());
        detail.setStatus(order.getStatus());
        detail.setSourceTroughNo(order.getSourceTroughNo());
        detail.setOperator(order.getOperator());
        detail.setSteps(stepRepository.findByBatchNoOrderByStepIndexAsc(order.getBatchNo()));
        return detail;
    }

    /** 结束工序的返回：最新工单详情 + 本次生成的清洗交接单号（可空，无设备工序不建单） */
    public static class FinishStepResult {
        private final OrderDetailDTO detail;
        private final String handoverNo;

        public FinishStepResult(OrderDetailDTO detail, String handoverNo) {
            this.detail = detail;
            this.handoverNo = handoverNo;
        }

        public OrderDetailDTO getDetail() {
            return detail;
        }

        public String getHandoverNo() {
            return handoverNo;
        }
    }
}
