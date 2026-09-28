package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.WitheringTrough;
import com.tea.factory.dto.WitheringRequest;
import com.tea.factory.repository.WitheringTroughRepository;

/**
 * 摊青与萎凋：登记环境温湿度、失水率，达标后转为「可杀青」。
 */
@Service
public class WitheringService {

    private final WitheringTroughRepository troughRepository;

    public WitheringService(WitheringTroughRepository troughRepository) {
        this.troughRepository = troughRepository;
    }

    public List<WitheringTrough> list() {
        return troughRepository.findAll();
    }

    /** 开槽登记 */
    @Transactional
    public WitheringTrough open(WitheringRequest request) {
        String troughNo = request.getTroughNo();
        if (troughNo == null || troughNo.isBlank()) {
            throw new BizException("槽号不能为空");
        }
        if (troughRepository.findByTroughNo(troughNo) != null) {
            throw new BizException("槽号 " + troughNo + " 已在用，请先下槽");
        }
        if (!Dict.isValidWitherMethod(request.getWitherMethod())) {
            throw new BizException("摊青方式只能是：" + String.join(" / ", Dict.WITHER_METHODS));
        }
        if (!Dict.isValidTeaType(request.getTeaType())) {
            throw new BizException("茶类不合法，可选：" + String.join("、", Dict.TEA_TYPES));
        }
        BigDecimal thickness = request.getLeafThicknessCm() == null ? BigDecimal.valueOf(3) : request.getLeafThicknessCm();
        if (thickness.compareTo(BigDecimal.valueOf(1)) < 0 || thickness.compareTo(BigDecimal.valueOf(15)) > 0) {
            throw new BizException("摊叶厚度应在 1~15 cm 之间，过厚会闷青、过薄会失水过快");
        }

        WitheringTrough trough = new WitheringTrough();
        trough.setTroughNo(troughNo);
        trough.setIntakeNo(request.getIntakeNo());
        trough.setBatchNo(request.getBatchNo());
        trough.setTeaType(request.getTeaType());
        trough.setWitherMethod(request.getWitherMethod());
        trough.setLeafThicknessCm(thickness);
        trough.setWitherHours(request.getWitherHours());
        trough.setTempC(request.getTempC());
        trough.setHumidityPct(request.getHumidityPct());
        trough.setLeafWeightKg(request.getLeafWeightKg());
        trough.setCurrentLossPct(request.getCurrentLossPct() == null ? BigDecimal.ZERO : request.getCurrentLossPct());
        trough.setTargetLossPct(Dict.targetLossPct(request.getTeaType()));
        trough.setStatus("WITHERING");
        trough.setUpdatedAt(LocalDateTime.now());
        return troughRepository.save(trough);
    }

    /** 登记环境温湿度与失水率 */
    @Transactional
    public WitheringTrough log(String troughNo, WitheringRequest request) {
        WitheringTrough trough = troughRepository.findByTroughNo(troughNo);
        if (trough == null) {
            throw new BizException("槽号 " + troughNo + " 不存在");
        }
        if ("DONE".equals(trough.getStatus())) {
            throw new BizException("槽号 " + troughNo + " 已下槽，不能继续登记");
        }
        if (request.getTempC() != null) {
            trough.setTempC(request.getTempC());
        }
        if (request.getHumidityPct() != null) {
            trough.setHumidityPct(request.getHumidityPct());
        }
        if (request.getWitherHours() != null) {
            trough.setWitherHours(request.getWitherHours());
        }
        if (request.getLeafThicknessCm() != null) {
            trough.setLeafThicknessCm(request.getLeafThicknessCm());
        }
        if (request.getCurrentLossPct() != null) {
            BigDecimal loss = request.getCurrentLossPct();
            if (loss.compareTo(BigDecimal.ZERO) < 0 || loss.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new BizException("失水率应在 0~90% 之间");
            }
            trough.setCurrentLossPct(loss);
        }
        trough.setStatus(trough.getCurrentLossPct().compareTo(trough.getTargetLossPct()) >= 0 ? "READY" : "WITHERING");
        trough.setUpdatedAt(LocalDateTime.now());
        return troughRepository.save(trough);
    }

    /** 下槽（摊青结束，鲜叶转投制茶工单） */
    @Transactional
    public WitheringTrough release(String troughNo) {
        WitheringTrough trough = troughRepository.findByTroughNo(troughNo);
        if (trough == null) {
            throw new BizException("槽号 " + troughNo + " 不存在");
        }
        if (trough.getCurrentLossPct().compareTo(trough.getTargetLossPct()) < 0) {
            throw new BizException("槽号 " + troughNo + " 当前失水率 " + trough.getCurrentLossPct()
                    + "% 未达目标 " + trough.getTargetLossPct() + "%，不允许下槽");
        }
        trough.setStatus("DONE");
        trough.setUpdatedAt(LocalDateTime.now());
        return troughRepository.save(trough);
    }

    public List<WitheringTrough> readyList() {
        return troughRepository.findByStatusOrderByTroughNoAsc("READY");
    }
}
