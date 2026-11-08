package com.laimengchao.sso.settings.module.bizdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 职位主数据（position）。岗位序列与职位名称
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_position")
public class BizPosition extends BaseEntity implements BizNamed {

    /** 取值编码，业务表里存的就是它（如 tech_dev / 技术开发） */
    private String code;
    /** 展示名 */
    private String name;
    /** 同级排序，小的在前 */
    private Integer sort;
    /** 1 启用 / 0 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 职位类别：管理类 / 技术类（仅职位主数据使用） */
    private String category;
    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;

    /* ---------------- 职级体系（仅职位主数据使用） ---------------- */

    /**
     * 职务族编码（如 DEV / TEST / ACC / RECRUIT / MGMT）。
     * <p>同一个族内是一条晋升通道：开发工程师族的初级 → 中级 → 高级 → 资深。
     */
    private String jobFamily;

    /** 职务族展示名（开发工程师 / 测试工程师 / 会计 / 招聘专员 …），下拉按它分组 */
    private String jobFamilyName;

    /** 序列：M 管理序列 / P 专业序列 */
    private String series;

    /** 等级序号：P 序列 1 助理 / 2 初级 / 3 中级 / 4 高级 / 5 资深；M 序列 1 经理 / 2 总监 / 3 高管 */
    private Integer levelNo;

    /** 等级展示名（助理 / 初级 / 中级 / 高级 / 资深 / 经理 / 总监 / 高管） */
    private String levelName;
}
