package com.laimengchao.sso.blog.internal;

import com.laimengchao.sso.common.entity.EntityMetaController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客库的「业务实体」元数据 + 取数端点——审批配置给 blog 场景绑表时用。
 * <p>
 * 实现来自 common 的 {@link EntityMetaController}：读当前 DataSource 的 information_schema，
 * 暴露的是 blog 库的表（文章、评论、栏目…）。准入由 {@code /internal/**} 的 X-Internal-Token
 * 拦截器负责（见 {@code config.WebMvcConfig}）。
 */
@RestController
@RequestMapping("/internal/entity")
public class InternalEntityController extends EntityMetaController {

    public InternalEntityController(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
}
