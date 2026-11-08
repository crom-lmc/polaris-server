package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人员信息变更单（主表）。一次「变更」提交一行：
 * 基本信息字段级差异在 {@link EmpChangeField}，银行卡/教育/工作/家庭成员的行级差异在 {@link EmpChangeRow}。
 * 审批通过回调时按这两份明细把档案与子表一次性落库；驳回/撤销只改本表 status。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_change")
public class EmpChange extends BaseEntity {

    private Long employeeId;
    /** 工号快照 */
    private String empNo;
    /** 姓名快照 */
    private String employeeName;
    private Long orgId;
    /** 所属组织全路径快照 */
    private String orgName;
    /** 0审批中 1已生效 2已驳回 3已撤销 */
    private Integer status;
    private Long applicantAccountId;
    /** 提交人姓名快照 */
    private String applicantName;
    /** 基本信息变更字段数（列表概览冗余） */
    private Integer baseChanges;
    /** 子表新增行数（列表概览冗余） */
    private Integer addCount;
    /** 子表修改行数（列表概览冗余） */
    private Integer modCount;
    /** 子表删除行数（列表概览冗余） */
    private Integer delCount;
    /** 审批中心实例ID（回调幂等比对用；流程结束保留痕迹） */
    private Long approvalInstanceId;
    /** 备注（驳回原因等） */
    private String remark;
}
