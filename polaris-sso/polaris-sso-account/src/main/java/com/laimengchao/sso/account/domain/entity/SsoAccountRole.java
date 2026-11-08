package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 账户-角色关联（无业务字段，仅主键复合）。
 */
@Data
@TableName("sso_account_role")
public class SsoAccountRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long accountId;
    private Long roleId;
}
