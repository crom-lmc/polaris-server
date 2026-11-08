package com.laimengchao.sso.settings.module.bizdata.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 主数据项 VO（六类通用：银行/学历/职位/请假类型/离职原因/家庭成员关系）。
 * id 序列化成字符串——雪花 19 位超 JS 安全整数，见 BaseEntity 注释。
 */
@Data
public class BizItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 类别编码：bank / education / position / leave_type / resign_reason / family_relation */
    private String type;

    /** 取值编码，业务表里存的就是它 */
    private String code;

    /** 展示名 */
    private String name;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private String remark;

    /** 职位类别（仅 position 类型有值：管理类 / 技术类），其余类型为 null */
    private String category;

    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;

    /* ---------------- 职级体系（仅 position 类型有值） ---------------- */

    /** 职务族编码（DEV / TEST / ACC / RECRUIT / MGMT …），同族内构成一条晋升通道 */
    private String jobFamily;

    /** 职务族展示名（开发工程师 / 会计 / 招聘专员 …） */
    private String jobFamilyName;

    /** 序列：M 管理 / P 专业 */
    private String series;

    /** 等级序号：P 1 助理 2 初级 3 中级 4 高级 5 资深；M 1 经理 2 总监 3 高管 */
    private Integer levelNo;

    /** 等级展示名 */
    private String levelName;
}
