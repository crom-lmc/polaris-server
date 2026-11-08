package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 请假「审批转办」选项：当前登录人是否部门负责人（决定该字段是否必填），
 * 以及可选的转办人（在职、已开户的其他员工）。
 */
@Data
public class LeaveDelegateOptionsVO {

    /** true = 当前登录人是部门负责人，请假时必须指定审批转办人 */
    private boolean required;
    private List<Option> options = new ArrayList<>();

    @Data
    public static class Option {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long accountId;
        private String name;
        private String orgName;
    }
}
