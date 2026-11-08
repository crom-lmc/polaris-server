package com.laimengchao.sso.settings.module.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项（二级）。同一 type_code 下 value 唯一；value 存字符串
 * （数字枚举如性别 1/2 由调用方自行转换），label 是展示名。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_dict_item")
public class DictItem extends BaseEntity {

    private String typeCode;
    /** 取值；业务表里存的就是它 */
    private String value;
    /** 展示名 */
    private String label;
    /** 同级排序，小的在前 */
    private Integer sort;
    /** 1 启用 / 0 停用 */
    private Integer status;
    /** 备注或扩展说明（JSON 串由调用方自行解释） */
    private String remark;
    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;
}
