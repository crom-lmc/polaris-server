package com.laimengchao.sso.settings.module.approval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 场景数据表绑定请求体（审批页面「与表绑定 / 真绑定」）。
 * <p>
 * 由配置中心管理端设置，描述一个业务场景要<b>真绑定</b>哪些业务库表：主体表（main=true，
 * 按审批单据关联的主键取）+ 若干子表（按 fk 反查，如 employee_id）。渲染时 settings 据此
 * 回调业务方 {@code /internal/entity/data/...} 把真实行捞出来。
 * <p>
 * 子表一律按外键反查出<b>多行</b>（{@code values[表名]} = List），列怎么展示交给「审批页面配置」：
 * 既能逐列加进表单，也能放进表格控件。绑定里不再存「关联方式」；
 * 旧 JSON 若还带 relation 字段会被忽略，不影响反序列化。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SceneTableBindingsDto {

    /**
     * 绑定所属应用（appCode）——决定去<b>哪个库</b>取数。
     * <p>
     * 表名只在各自库里唯一，ehr 与 cms 都可能有 t_article 之类同名表，所以「绑哪张表」
     * 必须和「哪个应用」一起记。保存时以场景的 appCode 为准（前端传什么都会被覆盖），
     * 避免把 ehr 场景的表绑到 cms 库上去。发起审批时随绑定一起冻结进实例快照，
     * 因此事后改场景所属应用也不影响在途单。
     */
    private String appCode;

    /**
     * 表单快照里<b>主体主键</b>的键名——主表按它取行。ehr 的审批单据快照里都带 employeeId，
     * 所以 ehr 场景通常填 employeeId；其它业务接入时按各自快照字段改写（如 cms 内容类场景用 articleId）。
     * 子表则按各自 fk 列反查。
     * <p>
     * <b>这里不预置默认值</b>：管理端要能看出「这个场景到底按哪个键取主表」，
     * 写错能立刻发现。留空时由后端按所属应用兜底（见 ApprovalContextService#defaultSubjectKey）。
     */
    private String subjectKey;

    /**
     * 场景级：「业务主体 ID」在 formData 里的键名（employeeId / orgId / articleId）。
     * 由管理端随绑定一起保存，落 {@code cfg_biz_scene.subject_field}；
     * latest-batch 与在途校验读它，不再依赖 Java 里的业务类型白名单。
     */
    private String subjectField;
    /** 场景级：SUBJECT / DOC，新单 bizId 的语义 */
    private String bizIdKind;

    @NotNull(message = "tables 不能为空")
    private List<BoundTable> tables = new ArrayList<>();

    public static final String MODE_MAIN_ROW = "MAIN_ROW";
    public static final String MODE_BIZ_ROW = "BIZ_ROW";
    public static final String MODE_FK_LIST = "FK_LIST";

    /** 单张被绑定的表 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoundTable {
        /** 业务库表名，必须真实存在于被绑定应用下（如 ehr_employee） */
        @NotBlank(message = "table 不能为空")
        private String table;
        /**
         * 外键列：仅 FK_LIST 用，按主体 ID 反查多行（例：ehr_emp_bank.employee_id）。
         * MAIN_ROW / BIZ_ROW 按主键取一行，不需要 fk。
         */
        private String fk;
        /** 前端展示用的中文名（如「银行卡」），可不填，缺省取表名 */
        private String label;
        /**
         * 兼容字段：旧配置的「是否主体表」。新配置一律用 mode 声明定位语义。
         * mode 缺失时按它推导（true → MAIN_ROW，false → FK_LIST），保证存量单不炸。
         */
        private boolean main;
        /**
         * 定位模式，承载「本次单据」与「业务主体」两种取数口径：
         * <ul>
         *   <li><b>MAIN_ROW</b>：按 subjectId 取主体表一行（ehr_employee / ehr_org_node），
         *       生成 {@code 表名.列名} 扁平键；</li>
         *   <li><b>BIZ_ROW</b>：按 {@code id = biz_id} 取<b>本次单据</b>一行
         *       （ehr_regularize_apply / ehr_leave / ehr_leader_change），同样生成扁平键，
         *       绝不按主体外键捞出全部历史单；</li>
         *   <li><b>FK_LIST</b>：档案子表，按 fk + subjectId 取多行（稳定排序），
         *       整表放进 values[表名]，只有它允许前端首行降级。</li>
         * </ul>
         */
        private String mode;

        /** 存量配置（无 mode）按 main/fk 推导；非法 mode 一律降级为 FK_LIST。 */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public String effectiveMode() {
            if (mode != null && !mode.isBlank()) {
                String m = mode.trim().toUpperCase(Locale.ROOT);
                if (MODE_MAIN_ROW.equals(m) || MODE_BIZ_ROW.equals(m) || MODE_FK_LIST.equals(m)) {
                    return m;
                }
            }
            return main ? MODE_MAIN_ROW : MODE_FK_LIST;
        }
    }
}
