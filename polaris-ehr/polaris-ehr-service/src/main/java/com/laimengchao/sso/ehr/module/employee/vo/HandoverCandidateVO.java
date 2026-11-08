package com.laimengchao.sso.ehr.module.employee.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 交接人候选（员工自助提交离职时下拉用）。
 * <p>
 * 刻意做成精简视图：只给「选人需要的信息」，不带手机号/邮箱——提交离职申请的人只是要挑一个
 * 同事接手工作，没有理由看到同子公司所有人的联系方式。
 */
@Data
public class HandoverCandidateVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String empNo;
    private String name;
    private String position;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    private String orgName;
    /** 组织全路径，下拉里做区分用 */
    private String orgPathName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String accountUsername;
    private Integer status;
}
