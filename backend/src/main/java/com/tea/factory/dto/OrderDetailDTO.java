package com.tea.factory.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tea.factory.domain.ProcessStepRecord;

/**
 * 工单详情：工单本体 + 工序序列进度 + 各工序执行记录。
 */
public class OrderDetailDTO {

    private String batchNo;
    private String teaType;
    private String teaTypeName;
    private List<String> flow = new ArrayList<>();
    private List<String> flowNames = new ArrayList<>();
    private String currentStep;
    private String currentStepName;
    private Integer currentStepIndex;
    private BigDecimal inputQtyKg;
    private BigDecimal outputQtyKg;
    private String status;
    private String sourceTroughNo;
    private String operator;
    private List<ProcessStepRecord> steps = new ArrayList<>();

    /** 最近一道工序收尾形成的清洗交接单号（无设备工序时为空） */
    private String lastHandoverNo;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getTeaType() {
        return teaType;
    }

    public void setTeaType(String teaType) {
        this.teaType = teaType;
    }

    public String getTeaTypeName() {
        return teaTypeName;
    }

    public void setTeaTypeName(String teaTypeName) {
        this.teaTypeName = teaTypeName;
    }

    public List<String> getFlow() {
        return flow;
    }

    public void setFlow(List<String> flow) {
        this.flow = flow;
    }

    public List<String> getFlowNames() {
        return flowNames;
    }

    public void setFlowNames(List<String> flowNames) {
        this.flowNames = flowNames;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public void setCurrentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
    }

    public Integer getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(Integer currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public BigDecimal getInputQtyKg() {
        return inputQtyKg;
    }

    public void setInputQtyKg(BigDecimal inputQtyKg) {
        this.inputQtyKg = inputQtyKg;
    }

    public BigDecimal getOutputQtyKg() {
        return outputQtyKg;
    }

    public void setOutputQtyKg(BigDecimal outputQtyKg) {
        this.outputQtyKg = outputQtyKg;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceTroughNo() {
        return sourceTroughNo;
    }

    public void setSourceTroughNo(String sourceTroughNo) {
        this.sourceTroughNo = sourceTroughNo;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<ProcessStepRecord> getSteps() {
        return steps;
    }

    public void setSteps(List<ProcessStepRecord> steps) {
        this.steps = steps;
    }

    public String getLastHandoverNo() {
        return lastHandoverNo;
    }

    public void setLastHandoverNo(String lastHandoverNo) {
        this.lastHandoverNo = lastHandoverNo;
    }
}
