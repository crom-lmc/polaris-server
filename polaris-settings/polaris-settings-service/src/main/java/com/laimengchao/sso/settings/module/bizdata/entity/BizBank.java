package com.laimengchao.sso.settings.module.bizdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 银行主数据（bank）。员工工资卡/报销卡的发卡行清单，跨服务被 ehr 的银行卡档案引用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bank")
public class BizBank extends BaseEntity implements BizNamed {

    /** 取值编码，业务表里存的就是它（如 icbc / 工商银行） */
    private String code;
    /** 展示名 */
    private String name;
    /** 同级排序，小的在前 */
    private Integer sort;
    /** 1 启用 / 0 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;
}
