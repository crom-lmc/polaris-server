package com.laimengchao.sso.openapi.module.endpoint.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.mapper.ApiOpenEndpointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 开放 API 目录管理（管理端）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointService {

    private final ApiOpenEndpointMapper mapper;

    public List<ApiOpenEndpoint> list() {
        return mapper.selectList(Wrappers.<ApiOpenEndpoint>lambdaQuery()
                .orderByAsc(ApiOpenEndpoint::getDirection, ApiOpenEndpoint::getApiCode));
    }

    /** 新建或按 api_code 更新。 */
    public void save(ApiOpenEndpoint req) {
        if (req.getApiCode() == null || req.getApiCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "api_code 必填");
        }
        if (req.getOperationType() == null || req.getOperationType().isBlank()) {
            req.setOperationType(inferOperationType(req));
        }
        ApiOpenEndpoint existing = mapper.selectByCode(req.getApiCode());
        if (existing == null) {
            req.setId(null);
            req.setCreatedAt(LocalDateTime.now());
            req.setUpdatedAt(LocalDateTime.now());
            if (req.getStatus() == null) {
                req.setStatus(1);
            }
            mapper.insert(req);
        } else {
            req.setId(existing.getId());
            req.setCreatedAt(existing.getCreatedAt());
            req.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(req);
        }
        log.info("开放 API 已保存：apiCode={}", req.getApiCode());
    }

    /** 未显式选择操作类型时：出站一律 EXECUTE；入站按 HTTP 方法推断。 */
    private String inferOperationType(ApiOpenEndpoint req) {
        if (req.getDirection() != null && req.getDirection() == 2) {
            return "EXECUTE";
        }
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase();
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "QUERY";
        };
    }

    public void toggle(String apiCode, int status) {
        ApiOpenEndpoint ep = mapper.selectByCode(apiCode);
        if (ep == null) {
            throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        }
        ep.setStatus(status);
        ep.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(ep);
        log.info("开放 API 状态变更：apiCode={} status={}", apiCode, status);
    }

    public void delete(String apiCode) {
        ApiOpenEndpoint ep = mapper.selectByCode(apiCode);
        if (ep == null) {
            throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        }
        mapper.deleteById(ep.getId());
        log.info("开放 API 已删除：apiCode={}", apiCode);
    }
}
