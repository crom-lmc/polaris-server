package com.laimengchao.sso.settings.module.embed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 埋点配置：一个 key 对应一份 JSON 值。
 * <p>
 * 供其他应用通过配置中心内部接口（settings-client 的 Feign）读取，
 * 避免把可变配置写死在各自服务的 yml 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_embed_point")
public class EmbedPoint extends BaseEntity {

    /** 埋点 key，如 ehr.account.default-roles */
    private String code;
    /** 所属应用编码，空=通用；左侧树按「应用 → 模块」分组 */
    private String appCode;
    /** 模块，如「人员管理」 */
    private String module;
    /** 描述 */
    private String name;
    /** 埋点值（JSON 文本） */
    private String value;
    /** 1 内置（种子数据） */
    private Integer builtIn;
    private Integer sort;
}
