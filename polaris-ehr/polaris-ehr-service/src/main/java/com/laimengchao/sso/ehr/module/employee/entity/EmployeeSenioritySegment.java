package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工司龄「前司工作经历」分段：首次工作不代表一直连续，一个人可能有多段、
 * 中间断档的工作经历，因此按段登记起止日期，每段单独上传证明，年限由后端合并加总。
 */
@Data
@TableName("ehr_employee_seniority_segment")
public class EmployeeSenioritySegment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long employeeId;
    /** 所属核定记录ID（ehr_employee_seniority_record.id） */
    private Long recordId;
    /** 单位名称（选填，便于 HR 核对） */
    private String company;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 该段工作证明附件ID（社保/离职证明，必传）；19 位雪花ID 必须转字符串，避免前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long proofFileId;
    private String proofFileName;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
