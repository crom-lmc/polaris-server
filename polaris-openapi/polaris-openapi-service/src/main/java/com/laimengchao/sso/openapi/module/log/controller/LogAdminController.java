package com.laimengchao.sso.openapi.module.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.service.LogQueryService;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import com.laimengchao.sso.openapi.security.admin.Permit;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 调用日志管理接口（SSO 用户令牌 + @Permit）。 */
@Slf4j
@RestController
@RequestMapping("/api/open/admin/log")
@RequiredArgsConstructor
public class LogAdminController {

    private final LogQueryService queryService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_LOG_LIST)
    public R<IPage<ApiCallLog>> list(@RequestParam(required = false) String direction,
                                     @RequestParam(required = false) String appCode,
                                     @RequestParam(required = false) String apiCode,
                                     @RequestParam(required = false) String channel,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) Boolean onlyFailed,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        LogQuery q = buildQuery(direction, appCode, apiCode, channel, from, to, onlyFailed, page, size);
        return R.ok(queryService.page(q));
    }

    @GetMapping("/detail")
    @Permit(PermissionCodes.OPENAPI_LOG_DETAIL)
    public R<?> detail(@RequestParam Long id) {
        return R.ok(queryService.detail(id));
    }

    /** 导出 CSV（UTF-8 BOM，Excel 可直接打开）；报文已在落库时脱敏。 */
    @GetMapping("/export")
    @Permit(PermissionCodes.OPENAPI_LOG_EXPORT)
    public void export(@RequestParam(required = false) String direction,
                       @RequestParam(required = false) String appCode,
                       @RequestParam(required = false) String apiCode,
                       @RequestParam(required = false) String channel,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) Boolean onlyFailed,
                       HttpServletResponse response) throws Exception {
        LogQuery q = buildQuery(direction, appCode, apiCode, channel, from, to, onlyFailed, 1, 20);
        List<ApiCallLog> rows = queryService.export(q);

        response.setContentType("text/csv;charset=UTF-8");
        String fileName = URLEncoder.encode("openapi-call-log.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，避免 Excel 中文乱码
        sb.append('\uFEFF');
        sb.append("id,trace_id,direction,app_code,api_code,channel,http_method,request_path,")
                .append("http_status,biz_code,cost_ms,client_ip,error_msg,created_at\n");
        for (ApiCallLog r : rows) {
            sb.append(csv(r.getId())).append(',').append(csv(r.getTraceId())).append(',')
                    .append(csv(r.getDirection())).append(',').append(csv(r.getAppCode())).append(',')
                    .append(csv(r.getApiCode())).append(',').append(csv(r.getChannel())).append(',')
                    .append(csv(r.getHttpMethod())).append(',').append(csv(r.getRequestPath())).append(',')
                    .append(csv(r.getHttpStatus())).append(',').append(csv(r.getBizCode())).append(',')
                    .append(csv(r.getCostMs())).append(',').append(csv(r.getClientIp())).append(',')
                    .append(csv(r.getErrorMsg())).append(',').append(csv(r.getCreatedAt())).append('\n');
        }
        response.getWriter().write(sb.toString());
        log.info("导出调用日志：rows={}", rows.size());
    }

    private LogQuery buildQuery(String direction, String appCode, String apiCode, String channel,
                                String from, String to, Boolean onlyFailed, int page, int size) {
        LogQuery q = new LogQuery();
        q.setDirection(direction);
        q.setAppCode(appCode);
        q.setApiCode(apiCode);
        q.setChannel(channel);
        q.setFrom(from);
        q.setTo(to);
        q.setOnlyFailed(onlyFailed);
        q.setPage(page);
        q.setSize(size);
        return q;
    }

    private String csv(Object o) {
        if (o == null) {
            return "";
        }
        String s = String.valueOf(o).replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
