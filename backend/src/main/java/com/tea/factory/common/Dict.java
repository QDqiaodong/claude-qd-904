package com.tea.factory.common;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制茶词典与工艺参数表：茶类、工序序列、鲜叶等级、审评维度、成品等级、常见缺陷、
 * 设备类型与状态、各工序的工艺阈值等。
 * 规则集中在此，便于调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 茶类 ----------------
    public static final String GREEN = "GREEN";
    public static final String RED = "RED";
    public static final String OOLONG = "OOLONG";
    public static final String WHITE = "WHITE";
    public static final String PUER = "PUER";

    public static final List<String> TEA_TYPES = List.of(GREEN, RED, OOLONG, WHITE, PUER);

    private static final Map<String, String> TEA_TYPE_NAME = Map.of(
            GREEN, "绿茶",
            RED, "红茶",
            OOLONG, "乌龙茶",
            WHITE, "白茶",
            PUER, "普洱茶");

    private static final Map<String, String> TEA_TYPE_CODE = Map.of(
            GREEN, "GREEN",
            RED, "BLACK",
            OOLONG, "OOLONG",
            WHITE, "WHITE",
            PUER, "PUER");

    // ---------------- 工序 ----------------
    public static final String STEP_SPREAD = "SPREAD";     // 摊青 / 萎凋 / 晒青
    public static final String STEP_MAKING = "MAKING";     // 做青（摇青）
    public static final String STEP_FIX = "FIX";           // 杀青
    public static final String STEP_ROLL = "ROLL";         // 揉捻
    public static final String STEP_FERMENT = "FERMENT";   // 发酵 / 渥堆
    public static final String STEP_DRY = "DRY";           // 干燥
    public static final String STEP_PICK = "PICK";         // 拣剔拼配

    private static final Map<String, String> STEP_NAME = Map.of(
            STEP_SPREAD, "摊青",
            STEP_MAKING, "做青",
            STEP_FIX, "杀青",
            STEP_ROLL, "揉捻",
            STEP_FERMENT, "发酵",
            STEP_DRY, "干燥",
            STEP_PICK, "拣剔");

    /** 红茶的发酵、普洱的渥堆，同一工序键在不同茶类下的叫法 */
    private static final Map<String, Map<String, String>> STEP_ALIAS = Map.of(
            RED, Map.of(STEP_FERMENT, "发酵", STEP_SPREAD, "萎凋"),
            PUER, Map.of(STEP_FERMENT, "渥堆", STEP_SPREAD, "摊青"),
            OOLONG, Map.of(STEP_SPREAD, "晒青"),
            WHITE, Map.of(STEP_SPREAD, "摊青（自然萎凋）"));

    /** 各茶类的工序序列（必须按顺序执行，不能跳步） */
    private static final Map<String, List<String>> TEA_FLOW = Map.of(
            GREEN, List.of(STEP_SPREAD, STEP_FIX, STEP_ROLL, STEP_DRY, STEP_PICK),
            RED, List.of(STEP_SPREAD, STEP_ROLL, STEP_FERMENT, STEP_DRY, STEP_PICK),
            OOLONG, List.of(STEP_SPREAD, STEP_MAKING, STEP_FIX, STEP_ROLL, STEP_DRY, STEP_PICK),
            WHITE, List.of(STEP_SPREAD, STEP_DRY, STEP_PICK),
            PUER, List.of(STEP_SPREAD, STEP_FIX, STEP_ROLL, STEP_FERMENT, STEP_DRY, STEP_PICK));

    /** 各茶类杀青温度下限（℃），低于该值不允许结束杀青进入下一工序 */
    private static final Map<String, BigDecimal> MIN_FIX_TEMP = Map.of(
            GREEN, BigDecimal.valueOf(220),
            OOLONG, BigDecimal.valueOf(200),
            PUER, BigDecimal.valueOf(180));

    /** 各茶类发酵 / 渥堆最短时长（分钟），不足不允许进入干燥 */
    private static final Map<String, Integer> MIN_FERMENT_MINUTES = Map.of(
            RED, 180,
            PUER, 720,
            OOLONG, 120);

    /** 各茶类摊青目标失水率（%） */
    private static final Map<String, BigDecimal> TARGET_LOSS_PCT = Map.of(
            GREEN, BigDecimal.valueOf(32),
            RED, BigDecimal.valueOf(38),
            OOLONG, BigDecimal.valueOf(26),
            WHITE, BigDecimal.valueOf(45),
            PUER, BigDecimal.valueOf(12));

    /** 各茶类成茶率（投料 -> 干茶） */
    private static final Map<String, BigDecimal> YIELD_RATE = Map.of(
            GREEN, BigDecimal.valueOf(0.22),
            RED, BigDecimal.valueOf(0.24),
            OOLONG, BigDecimal.valueOf(0.20),
            WHITE, BigDecimal.valueOf(0.26),
            PUER, BigDecimal.valueOf(0.25));

    // ---------------- 鲜叶等级 ----------------
    public static final String BUD = "单芽";
    public static final String ONE_BUD_ONE_LEAF = "一芽一叶";
    public static final String ONE_BUD_TWO_LEAF = "一芽二叶";
    public static final String OPPOSITE_LEAF = "对夹叶";

    public static final List<String> LEAF_GRADES = List.of(BUD, ONE_BUD_ONE_LEAF, ONE_BUD_TWO_LEAF, OPPOSITE_LEAF);

    /** 各鲜叶等级收购单价（元/kg） */
    private static final Map<String, BigDecimal> LEAF_GRADE_PRICE = Map.of(
            BUD, BigDecimal.valueOf(118),
            ONE_BUD_ONE_LEAF, BigDecimal.valueOf(86),
            ONE_BUD_TWO_LEAF, BigDecimal.valueOf(52),
            OPPOSITE_LEAF, BigDecimal.valueOf(24));

    // ---------------- 摊青方式 ----------------
    public static final String SUN_WITHHER = "日光晒青";
    public static final String INDOOR_WITHHER = "室内萎凋";
    public static final List<String> WITHER_METHODS = List.of(SUN_WITHHER, INDOOR_WITHHER);

    // ---------------- 审评维度（五项，权重合计 100） ----------------
    public static final List<String> EVAL_DIMS = List.of("外形", "汤色", "香气", "滋味", "叶底");

    private static final Map<String, BigDecimal> EVAL_WEIGHT = Map.of(
            "外形", BigDecimal.valueOf(0.25),
            "汤色", BigDecimal.valueOf(0.10),
            "香气", BigDecimal.valueOf(0.25),
            "滋味", BigDecimal.valueOf(0.30),
            "叶底", BigDecimal.valueOf(0.10));

    // ---------------- 成品等级 ----------------
    public static final String SPECIAL = "特级";
    public static final String FIRST = "一级";
    public static final String SECOND = "二级";
    public static final String THIRD = "三级";

    public static final List<String> FINISH_GRADES = List.of(SPECIAL, FIRST, SECOND, THIRD);

    // ---------------- 常见缺陷 ----------------
    public static final List<String> DEFECTS = List.of(
            "红变叶", "焦边爆点", "断碎", "水闷味", "霉变", "老叶夹杂", "虫咬叶", "异味吸附");

    // ---------------- 设备 ----------------
    public static final String EQ_FIX = "杀青机";
    public static final String EQ_ROLL = "揉捻机";
    public static final String EQ_FERMENT = "发酵室";
    public static final String EQ_DRY = "烘干机";
    public static final String EQ_AROMA = "提香机";

    public static final List<String> EQUIPMENT_TYPES = List.of(EQ_FIX, EQ_ROLL, EQ_FERMENT, EQ_DRY, EQ_AROMA);

    /** 设备状态 */
    private static final Map<String, String> EQUIPMENT_STATUS = Map.of(
            "RUNNING", "运行",
            "IDLE", "待机",
            "CLEANING", "清洗",
            "FAULT", "故障");

    public static final List<String> EQUIPMENT_STATUS_KEYS = List.of("RUNNING", "IDLE", "CLEANING", "FAULT");

    // ---------------- 状态 ----------------
    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PROCESSING", "在制",
            "DONE", "已完成");

    private static final Map<String, String> TROUGH_STATUS = Map.of(
            "WITHERING", "摊青中",
            "READY", "可杀青",
            "DONE", "已下槽");

    private static final Map<String, String> STEP_STATUS = Map.of(
            "RUNNING", "执行中",
            "DONE", "已完成");

    private static final Map<String, String> INTAKE_STATUS = Map.of(
            "ACCEPTED", "合格入厂",
            "DEDUCTED", "扣重验收",
            "REJECTED", "退回");

    // ---------------- 业务阈值 ----------------
    /** 鲜叶含水量上限（%），超过要扣重 */
    public static final BigDecimal MAX_MOISTURE = BigDecimal.valueOf(78);
    /** 鲜叶杂质率上限（%），超过要扣重 */
    public static final BigDecimal MAX_IMPURITY = BigDecimal.valueOf(5);
    /** 含水量每超 1% 的扣重比例（%） */
    public static final BigDecimal MOISTURE_DEDUCT_PER_PCT = BigDecimal.valueOf(1.5);
    /** 杂质率每超 1% 的扣重比例（%） */
    public static final BigDecimal IMPURITY_DEDUCT_PER_PCT = BigDecimal.valueOf(2.0);
    /** 单次扣重比例上限（%），超过则建议退回 */
    public static final BigDecimal MAX_DEDUCT_RATE = BigDecimal.valueOf(30);
    /** 成品出厂水分上限（%），超过不允许入库 */
    public static final BigDecimal MAX_PRODUCT_MOISTURE = BigDecimal.valueOf(7);

    // ---------------- 取值方法 ----------------

    public static boolean isValidTeaType(String teaType) {
        return teaType != null && TEA_TYPE_NAME.containsKey(teaType);
    }

    public static String teaTypeName(String teaType) {
        return teaType == null ? "" : TEA_TYPE_NAME.getOrDefault(teaType, teaType);
    }

    /** 工单批次号中间段的茶类代码 */
    public static String teaTypeCode(String teaType) {
        return teaType == null ? "GREEN" : TEA_TYPE_CODE.getOrDefault(teaType, "GREEN");
    }

    public static boolean isValidStep(String step) {
        return step != null && STEP_NAME.containsKey(step);
    }

    /** 工序显示名（红茶叫发酵、普洱叫渥堆） */
    public static String stepName(String teaType, String step) {
        if (step == null) {
            return "";
        }
        Map<String, String> alias = STEP_ALIAS.getOrDefault(teaType, Map.of());
        return alias.getOrDefault(step, STEP_NAME.getOrDefault(step, step));
    }

    public static String stepName(String step) {
        return stepName(null, step);
    }

    /** 茶类的工序序列 */
    public static List<String> flow(String teaType) {
        return TEA_FLOW.getOrDefault(teaType, TEA_FLOW.get(GREEN));
    }

    public static int stepIndex(String teaType, String step) {
        return flow(teaType).indexOf(step);
    }

    public static BigDecimal minFixTemp(String teaType) {
        return MIN_FIX_TEMP.getOrDefault(teaType, BigDecimal.valueOf(200));
    }

    public static int minFermentMinutes(String teaType) {
        return MIN_FERMENT_MINUTES.getOrDefault(teaType, 120);
    }

    public static BigDecimal targetLossPct(String teaType) {
        return TARGET_LOSS_PCT.getOrDefault(teaType, BigDecimal.valueOf(30));
    }

    public static BigDecimal yieldRate(String teaType) {
        return YIELD_RATE.getOrDefault(teaType, BigDecimal.valueOf(0.22));
    }

    public static boolean isValidLeafGrade(String grade) {
        return grade != null && LEAF_GRADE_PRICE.containsKey(grade);
    }

    public static BigDecimal leafGradePrice(String grade) {
        return LEAF_GRADE_PRICE.getOrDefault(grade, BigDecimal.valueOf(24));
    }

    public static boolean isValidWitherMethod(String method) {
        return method != null && WITHER_METHODS.contains(method);
    }

    /** 审评维度权重（合计 1.0） */
    public static BigDecimal evalWeight(String dim) {
        return EVAL_WEIGHT.getOrDefault(dim, BigDecimal.ZERO);
    }

    /** 按总分判定成品等级；低于 60 分不入等 */
    public static String gradeOf(BigDecimal total) {
        if (total == null) {
            return "不入等";
        }
        double v = total.doubleValue();
        if (v >= 90) {
            return SPECIAL;
        }
        if (v >= 80) {
            return FIRST;
        }
        if (v >= 70) {
            return SECOND;
        }
        if (v >= 60) {
            return THIRD;
        }
        return "不入等";
    }

    public static boolean isValidFinishGrade(String grade) {
        return grade != null && FINISH_GRADES.contains(grade);
    }

    public static boolean isValidEquipmentType(String type) {
        return type != null && EQUIPMENT_TYPES.contains(type);
    }

    public static boolean isValidEquipmentStatus(String status) {
        return status != null && EQUIPMENT_STATUS.containsKey(status);
    }

    public static String equipmentStatusName(String status) {
        return status == null ? "" : EQUIPMENT_STATUS.getOrDefault(status, status);
    }

    public static String orderStatusName(String status) {
        return status == null ? "" : ORDER_STATUS.getOrDefault(status, status);
    }

    public static String troughStatusName(String status) {
        return status == null ? "" : TROUGH_STATUS.getOrDefault(status, status);
    }

    public static String stepStatusName(String status) {
        return status == null ? "" : STEP_STATUS.getOrDefault(status, status);
    }

    public static String intakeStatusName(String status) {
        return status == null ? "" : INTAKE_STATUS.getOrDefault(status, status);
    }

    /** 给页面用的完整词典（茶类、工序、等级、缺陷等） */
    public static Map<String, Object> vocab() {
        Map<String, Object> vocab = new LinkedHashMap<>();
        vocab.put("teaTypes", TEA_TYPES);
        vocab.put("teaTypeName", TEA_TYPE_NAME);
        vocab.put("leafGrades", LEAF_GRADES);
        vocab.put("leafGradePrice", LEAF_GRADE_PRICE);
        vocab.put("witherMethods", WITHER_METHODS);
        vocab.put("evalDims", EVAL_DIMS);
        vocab.put("finishGrades", FINISH_GRADES);
        vocab.put("defects", DEFECTS);
        vocab.put("equipmentTypes", EQUIPMENT_TYPES);
        vocab.put("equipmentStatus", EQUIPMENT_STATUS);
        vocab.put("stepName", STEP_NAME);
        vocab.put("teaFlow", TEA_FLOW);
        vocab.put("minFixTemp", MIN_FIX_TEMP);
        vocab.put("minFermentMinutes", MIN_FERMENT_MINUTES);
        vocab.put("targetLossPct", TARGET_LOSS_PCT);
        vocab.put("thresholds", Map.of(
                "maxMoisture", MAX_MOISTURE,
                "maxImpurity", MAX_IMPURITY,
                "maxDeductRate", MAX_DEDUCT_RATE,
                "maxProductMoisture", MAX_PRODUCT_MOISTURE));
        return vocab;
    }
}
