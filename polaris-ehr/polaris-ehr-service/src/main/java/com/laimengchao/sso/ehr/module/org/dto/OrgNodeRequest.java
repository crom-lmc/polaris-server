package com.laimengchao.sso.ehr.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 组织节点新增/编辑入参。parentId 为空表示根节点（只能是集团） */
@Data
public class OrgNodeRequest {

    private Long parentId;

    /** GROUP/COMPANY/DEPARTMENT/PROJECT/SUB_PROJECT */
    @NotBlank(message = "组织类型必填")
    private String type;

    private String code;

    @NotBlank(message = "节点名称必填")
    private String name;

    /** 组织简称 */
    private String shortName;
    /** 部门公用邮箱 */
    private String email;
    /** 办公地点 */
    private String address;
    /** 编制人数 */
    private Integer headcount;
    /** 成本中心 / 费用归属 */
    private String costCenter;
    /** 成立日期 yyyy-MM-dd */
    private String establishDate;

    private String leader;

    /** 负责人（员工档案ID）：设置后该节点下的请假/离职由这个人审批 */
    private Long leaderEmployeeId;

    private String phone;
    private Integer sort;
    private Integer status;
    private String remark;
}
