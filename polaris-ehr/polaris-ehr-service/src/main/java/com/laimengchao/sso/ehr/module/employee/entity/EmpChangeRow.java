package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员信息变更·子表行明细：一行一条「新增/删除/修改」。
 * old_json/new_json 是行快照（含字典展示名，如 bankName/degreeName/relationName），
 * 审批对比页直接拿它渲染，不需要再查字典；回调落库时按 row_key + op 应用到子表。
 */
@Data
@TableName("ehr_emp_change_row")
public class EmpChangeRow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 变更单ID（ehr_emp_change.id） */
    private Long changeId;
    /** BANK 银行卡 / EDUCATION 教育背景 / WORK 工作经历 / FAMILY 家庭成员 */
    private String listType;
    /** 原行ID（ehr_emp_bank 等子表主键）；ADD 行为空 */
    private String rowKey;
    /** ADD 新增 / DEL 删除 / MOD 修改 */
    private String op;
    /** 变更前行快照（JSON；ADD 为 NULL） */
    private String oldJson;
    /** 变更后行快照（JSON；DEL 为 NULL） */
    private String newJson;
    /** 展示顺序（沿用档案展示顺序，新增行排最后） */
    private Integer seq;

    private LocalDateTime createdAt;
}
