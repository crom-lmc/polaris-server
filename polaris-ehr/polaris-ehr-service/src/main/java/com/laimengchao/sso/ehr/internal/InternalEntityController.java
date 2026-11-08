package com.laimengchao.sso.ehr.internal;

import com.laimengchao.sso.common.entity.EntityMetaController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EHR 库的「业务实体」元数据 + 取数端点——审批页面「与表绑定」能力在 EHR 侧的挂载点。
 * <p>
 * 实现已上移到 {@link EntityMetaController}（common）：审批配置的数据绑定要支持多个模块，
 * 每个模块连的库不同但逻辑完全一致，没必要各写一遍。这里只声明「我是 EHR 的入口」，
 * 准入仍由本服务的 {@code /internal/**} 令牌拦截器把关。
 * <p>
 * 因为读的是当前 DataSource 的 information_schema，这个端点天然只暴露 EHR 库的表。
 */
@Slf4j
@RestController
@RequestMapping("/internal/entity")
public class InternalEntityController extends EntityMetaController {

    public InternalEntityController(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }
}
