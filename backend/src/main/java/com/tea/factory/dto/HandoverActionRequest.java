package com.tea.factory.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 清洗人员填写交接单：开始清洗（方式/起始时间）、完成清洗（残留风险/结束时间）可分步提交，
 * 也可一次填齐。报障、维修完成同样复用本请求里的故障/维修字段。
 * 必填项由服务按动作分别校验（如确认动作才必须有 confirmNote）。
 */
@Data
public class HandoverActionRequest {

    /** 操作人：清洗人 / 报障人 / 维修人 / 确认负责人 */
    private String operator;

    /** 残留风险：低 / 中 / 高 */
    private String residueRisk;

    /** 残留说明 */
    private String residueNote;

    /** 清洗方式 */
    private String cleanMethod;

    /** 清洗补充说明 */
    private String cleanNote;

    /** 清洗开始时间（不传默认取服务端当前时间） */
    private LocalDateTime cleanStartedAt;

    /** 清洗结束时间（不传默认取服务端当前时间） */
    private LocalDateTime cleanFinishedAt;

    /** 报障现象 */
    private String faultNote;

    /** 维修处理说明 */
    private String repairNote;

    /** 负责人确认意见 */
    private String confirmNote;
}
