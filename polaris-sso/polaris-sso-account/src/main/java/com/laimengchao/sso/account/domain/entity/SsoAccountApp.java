package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 账号-应用授权（复合主键，无业务字段）。
 * <p>
 * 与 account-role 一样，MyBatis-Plus 对复合主键支持有限：这里用 accountId 作
 * {@code @TableId} 以便按账户批量删，查询一律走 Wrapper 显式条件。
 */
@Data
@TableName("sso_account_app")
public class SsoAccountApp implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long accountId;
    private Long appId;
}
