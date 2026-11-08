package com.laimengchao.sso.settings.module.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型（一级）。code 全局唯一，如 ehr.leave_type / general.yes_no。
 * app_code 为空表示全局字典，否则只归属该应用；内置类型禁删改编码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_dict_type")
public class DictType extends BaseEntity {

    /** 类型编码，唯一；小写字母开头，仅小写字母/数字/点/下划线/中划线 */
    private String code;
    /** 展示名，如「请假类型」 */
    private String name;
    /** 归属应用 code；空 = 全局字典 */
    private String appCode;
    private String remark;
    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;
    /** 1 启用 / 0 停用；停用后内部接口也不再返回 */
    private Integer status;
}
