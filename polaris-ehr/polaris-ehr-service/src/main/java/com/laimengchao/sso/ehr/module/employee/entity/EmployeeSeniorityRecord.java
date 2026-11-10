package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工司龄核定记录：每次核定追加一条，保留历史。
 * 表无 updated_at，故不继承 BaseEntity。
 */
@Data
@TableName("ehr_employee_seniority_record")
public class EmployeeSeniorityRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long employeeId;
    /** 核定生效日 */
    private LocalDate effectiveDate;
    /** 司龄起算日（本单位） */
    private LocalDate seniorityStartDate;
    /** 首次参加工作日期（含前司） */
    private LocalDate firstWorkDate;
    /** 前司累计年限（社保核定） */
    private BigDecimal priorYears;
    /** 累计工龄（用于年假档位） */
    private BigDecimal totalYears;
    /** SOCIAL_SECURITY 社保核定 / MANUAL 手工 / FIRST_HIRE 入职默认 */
    private String source;
    private Long proofFileId;
    private String remark;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
