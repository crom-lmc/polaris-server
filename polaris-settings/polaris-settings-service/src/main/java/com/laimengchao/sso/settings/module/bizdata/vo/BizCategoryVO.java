package com.laimengchao.sso.settings.module.bizdata.vo;

import lombok.Data;

/**
 * 主数据类别 VO：管理页左侧/Tab 用的类别清单（固定六类，不可新增类别）。
 */
@Data
public class BizCategoryVO {

    /** 类别编码：bank / education / position / leave_type / resign_reason / family_relation */
    private String type;

    /** 展示名，如「银行」 */
    private String name;

    private String remark;

    /** 该类别下的数据项总数（含停用） */
    private Long itemCount;
}
