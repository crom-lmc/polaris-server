package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务场景注册表。业务方启动时上报，配置器据此出「字段池」。
 * <p>
 * formSchema 与 fieldSchema 是两份字典，不能合并：
 * 前者是条件表达式能引用的变量，后者是页面能展示的字段（含派生与系统字段）。
 * 合并会让条件编辑器下拉里冒出「档案完整度」这种没法比较的字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_biz_scene")
public class BizScene extends BaseEntity {

    private String appCode;
    private String bizType;
    private String bizName;
    /** 条件变量字典 JSON */
    private String formSchema;
    /** 页面字段池 JSON */
    private String fieldSchema;
    /**
     * 数据表绑定 JSON（审批页面「与表绑定 / 真绑定」用）。
     * <p>
     * 结构：{@code {"tables":[{"table":"ehr_employee","fk":null,"label":"人员基本信息","main":true},
     * {"table":"ehr_emp_bank","fk":"employee_id","label":"银行卡"}]}}。
     * main=true 的是主体表（按审批单据关联的主键取），其余按 fk 反查子表行。
     * 由配置中心管理端设置，业务方 registerScene 不会覆盖（遵循 formSchema/fieldSchema 同款约定）。
     */
    private String tableBindings;
    /**
     * 表单快照里「业务主体 ID」的键名（如 employeeId / orgId）。
     * latest-batch、在途校验、回调定位统一读这里，不再靠 Java 里的业务类型白名单。
     */
    private String subjectField;
    /** 该场景新单 bizId 的语义：SUBJECT / DOC。发起时可被请求显式覆盖。 */
    private String bizIdKind;
    private String permHint;
    /** REGISTER 业务注册 / MANUAL 手工维护 */
    private String source;
    private Integer sort;
}
