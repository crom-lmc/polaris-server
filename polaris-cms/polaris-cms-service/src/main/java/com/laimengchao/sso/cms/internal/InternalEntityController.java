package com.laimengchao.sso.cms.internal;

import com.laimengchao.sso.common.entity.EntityMetaController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CMS 库的「业务实体」元数据 + 取数端点——审批配置给 cms 场景（内容发布）绑表时用。
 * <p>
 * 实现来自 common 的 {@link EntityMetaController}：读当前 DataSource 的 information_schema，
 * 所以这里暴露的自然是 cms 库的表（cms_article / cms_category / cms_media …）。
 * 准入由本服务 {@code /internal/**} 的 X-Internal-Token 拦截器负责。
 */
@RestController
@RequestMapping("/internal/entity")
public class InternalEntityController extends EntityMetaController {

    public InternalEntityController(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
}
