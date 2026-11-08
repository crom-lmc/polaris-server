package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

/**
 * 业务主数据项（银行/学历/职位/请假类型/离职原因/家庭成员关系）。
 * 与字典项 DictItemDTO 刻意分开——主数据是独立真表（biz_bank 等），不是字典，
 * 语义与后续演进路径都不同，不共用 DTO。
 */
@Data
public class BizItemDTO {

    private String id;

    /** 取值编码，业务表里存的就是它 */
    private String code;

    /** 展示名 */
    private String name;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private String remark;
}
