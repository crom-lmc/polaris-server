package com.laimengchao.sso.settings.internal;

import com.laimengchao.sso.common.entity.EntityMetaController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心库的「业务实体」元数据 + 取数端点。
 * <p>
 * 审批配置管理端自己也是「一个应用」（appCode=settings），把它接进来是为了口径统一：
 * 前端选任意应用都能拿到表清单，不必为 settings 特判。绑定的是 settings 库的表
 * （字典、业务点、审批配置本身……）。
 * <p>
 * 注意与 {@code /api/settings/approval/entity/**}（对外代理，按 appCode 路由到各业务方）区分：
 * 这里是<b>本服务自身</b>的实体接口，走 {@code /internal/**}，只给服务间调用。
 */
@RestController
@RequestMapping("/internal/entity")
public class InternalEntityController extends EntityMetaController {

    public InternalEntityController(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
}
