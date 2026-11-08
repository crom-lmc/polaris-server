package com.laimengchao.sso.account.internal;

import com.laimengchao.sso.common.entity.EntityMetaController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一账号中心库的「业务实体」元数据 + 取数端点（appCode=sso-center）。
 * <p>
 * 暴露的是 sso_ms 库的表（账号、角色、权限、应用注册…）。审批配置里给「账号 / 权限类」场景
 * 绑表时用。准入由 {@code /internal/**} 的 X-Internal-Token 拦截器把关。
 */
@RestController
@RequestMapping("/internal/entity")
public class InternalEntityController extends EntityMetaController {

    public InternalEntityController(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
}
