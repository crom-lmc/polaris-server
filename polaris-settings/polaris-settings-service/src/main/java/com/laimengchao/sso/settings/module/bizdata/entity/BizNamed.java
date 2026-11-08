package com.laimengchao.sso.settings.module.bizdata.entity;

/**
 * 主数据实体的统一读取视图。
 * <p>
 * 六类主数据（银行/学历/职位/请假类型/离职原因/家庭成员关系）各有一张真表
 * （biz_bank、biz_education……），字段结构当前一致（code/name/sort/status/remark），
 * 但刻意不合并成一张通用表——每类将来大概率长出自己的专属字段
 * （银行的联行号、职位的职级、请假类型的扣薪规则），真表才是可扩展的归宿。
 * <p>
 * 本接口只做两件事：让 {@code BizItemService} 能用一张 Map 分发六张表的
 * CRUD 与读取，而不用写六份重复 service；给「某类要加专属字段」留出干净的演进路径
 * （加字段不影响本接口，特殊逻辑写在对应 entity / 单独逻辑里）。
 */
public interface BizNamed {

    Long getId();

    String getCode();

    void setCode(String code);

    String getName();

    void setName(String name);

    Integer getSort();

    void setSort(Integer sort);

    /** 1 启用 / 0 停用 */
    Integer getStatus();

    void setStatus(Integer status);

    String getRemark();

    void setRemark(String remark);

    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    Integer getBuiltIn();

    void setBuiltIn(Integer builtIn);
}
