package com.laimengchao.sso.settings.module.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.module.approval.config.EntityEndpointProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按 appCode 找对端、调 {@code /internal/entity/**} 的统一出口。
 * <p>
 * 所有「业务实体」取数都从这里走，好处是路由规则只有一处：
 * 配置期（管理端拉表清单 / 列元数据）与运行期（审批详情页捞真实行）用的是同一套 appCode，
 * 不会出现「配的时候能选、看的时候取不到」这种口径分裂。
 * <p>
 * <b>失败策略分两类</b>：
 * <ul>
 *   <li>配置期 {@link #tables}/{@link #columns} —— 用户正盯着界面操作，未开通要明确报错，
 *       否则下拉空着没人知道为什么；</li>
 *   <li>运行期 {@link #row}/{@link #rowsByFk} —— 审批单的渲染不能因为绑定的表被删/对端抖动而白屏，
 *       一律降级成「取不到」返回 null。</li>
 * </ul>
 */
@Slf4j
@Component
public class EntityDataClient {

    private final InternalHttpClient http;
    private final EntityEndpointProperties endpoints;

    public EntityDataClient(InternalHttpClient http, EntityEndpointProperties endpoints) {
        this.http = http;
        this.endpoints = endpoints;
    }

    /** 表清单（配置期）。未开通数据绑定的应用直接报错，前端据此提示。 */
    public JsonNode tables(String appCode) {
        return http.getData(baseUrlOrThrow(appCode) + "/internal/entity/tables");
    }

    /** 列元数据（配置期）。 */
    public JsonNode columns(String appCode, String table) {
        return http.getData(baseUrlOrThrow(appCode) + "/internal/entity/tables/" + table + "/columns");
    }

    /** 主体表一行（运行期）。取不到返回 null，调用方按「无数据」渲染。 */
    public JsonNode row(String appCode, String table, String id) {
        String base = endpoints.find(appCode);
        if (base == null) {
            log.debug("应用 {} 未配置数据绑定基址，跳过取数 table={}", appCode, table);
            return null;
        }
        return http.getData(base + "/internal/entity/data/" + table + "/" + id);
    }

    /** 子表多行（运行期），按外键反查。 */
    public JsonNode rowsByFk(String appCode, String table, String fk, String fkValue) {
        String base = endpoints.find(appCode);
        if (base == null) {
            log.debug("应用 {} 未配置数据绑定基址，跳过取数 table={}", appCode, table);
            return null;
        }
        return http.getData(base + "/internal/entity/data/" + table + "?fk=" + fk + "&fkValue=" + fkValue);
    }

    /** 已开通数据绑定的应用（配置端可选择绑表的范围） */
    public java.util.Map<String, String> availableApps() {
        return endpoints.available();
    }

    private String baseUrlOrThrow(String appCode) {
        String base = endpoints.find(appCode);
        if (base == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "应用「" + appCode + "」未开通数据绑定（该应用没有业务库或未配置对内地址）");
        }
        return base;
    }
}
