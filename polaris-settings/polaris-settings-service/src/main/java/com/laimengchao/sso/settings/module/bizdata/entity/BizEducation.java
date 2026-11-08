package com.laimengchao.sso.settings.module.bizdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学历主数据（education）。教育背景与最高学历共用一套学历层次
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_education")
public class BizEducation extends BaseEntity implements BizNamed {

    /** 取值编码，业务表里存的就是它（如 bachelor / 本科） */
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
