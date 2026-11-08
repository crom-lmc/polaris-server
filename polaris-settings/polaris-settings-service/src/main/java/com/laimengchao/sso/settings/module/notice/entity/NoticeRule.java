package com.laimengchao.sso.settings.module.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办/消息推送配置：一个事件编码对应一条配置。
 * <p>
 * 站内方式只有「web 待办」和「web 消息」两种且互斥（channel 单列即可保证），
 * 邮件是独立开关，可与任意一种叠加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_notice_rule")
public class NoticeRule extends BaseEntity {

    /** 事件编码，如 approval.approved；业务方按它触发 */
    private String code;
    /** 归属应用编码，空=通用；配置树按「应用 → 模块」分组 */
    private String appCode;
    /** 模块，如「审批中心」 */
    private String module;
    /** 配置描述，如「审批通过通知发起人」 */
    private String name;
    /** 标题模板，{变量} 由发送方传入 */
    private String titleTpl;
    /** 内容模板 */
    private String contentTpl;
    /** 站内方式：1 待办 / 2 消息 */
    private Integer channel;
    /** 1 同时发邮件 / 0 不发 */
    private Integer email;
    /** 1 启用 / 0 停用 */
    private Integer enabled;
    /** 1 内置（种子数据，禁删）/ 0 自定义 */
    private Integer builtIn;
    private Integer sort;
}
