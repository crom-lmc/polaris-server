package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账户实体。注意：这是「账户（Account）」而非「用户（User）」。
 * 用户（现实中的人）由 HR / 主数据系统管辖，账户只负责登录凭据与授权。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sso_account")
public class SsoAccount extends BaseEntity {

    private String username;
    private String nickname;

    /**
     * 邮箱。空邮箱必须以 SQL NULL 落库：uk_email 唯一索引对多个 NULL 放行，对多个 "" 不放行。
     * <p>
     * {@code insertStrategy = ALWAYS} 是必需的——MyBatis-Plus 默认 NOT_NULL 会把 null 字段
     * 从 INSERT 语句里剔掉，于是数据库默认值补位（历史库默认值还是 ''），
     * 第二个无邮箱账户照样撞唯一索引。这个坑非常隐蔽，务必保留。
     * update 保持默认策略：null 表示「不修改该字段」。
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private String email;

    private String phone;
    private String passwordHash;
    private String avatarUrl;

    /** @see com.laimengchao.sso.common.enums.AccountStatus */
    private Integer status;

    /** 来源：local / email / wx 等 */
    private String source;

    /** 1=下次登录强制改密 */
    private Integer pwdResetRequired;

    private Integer loginFailCount;

    /**
     * 锁定截止时间。这个字段的 null 是<b>有业务语义</b>的：解锁 / 改密 / 复职都要把它清空。
     * <p>
     * MyBatis-Plus 的 updateById 默认 NOT_NULL 策略会把 null 字段从 SET 里剔掉，
     * 于是 unlock() 里写了 setLockedUntil(null) 却根本没落库——状态变回正常了，
     * locked_until 还挂着半小时前的时间戳。这里单独放开该字段的更新策略。
     * 本类所有 updateById 传的都是查库得到的完整实体（不是只 set 几个字段的新对象），
     * 所以放开后不会在别的更新路径上误清锁定时间。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    /** 关联 HR 用户ID（人）。一个人可有多个账户。 */
    private Long userId;
}
