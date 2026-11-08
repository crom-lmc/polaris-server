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
 * 人员信息变更·基本信息字段明细：一行一个被改字段（原值/新值）。
 * 不继承 BaseEntity：明细行随主表走逻辑，不需要逻辑删除与更新时间。
 */
@Data
@TableName("ehr_emp_change_field")
public class EmpChangeField implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 变更单ID（ehr_emp_change.id） */
    private Long changeId;
    /** 字段键（与 ehr_employee 列对应，如 mobile） */
    private String fieldKey;
    /** 字段名快照（如 手机号） */
    private String fieldLabel;
    /** 变更前（提交时档案值；空串表示原本为空） */
    private String oldValue;
    /** 变更后（本次申请值） */
    private String newValue;
    /** 展示顺序（与变更表单字段顺序一致） */
    private Integer seq;

    private LocalDateTime createdAt;
}
