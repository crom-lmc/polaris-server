package com.laimengchao.sso.settings.module.embed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.settings.module.approval.service.InternalHttpClient;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointCreate;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointPatch;
import com.laimengchao.sso.settings.module.embed.service.EmbedPointService;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointHistoryVO;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointVO;
import com.laimengchao.sso.settings.security.Permit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端埋点配置（settings-web 使用）。
 * 查看 embed:view，编辑 embed:manage。
 */
@RestController
@RequestMapping("/api/settings/embed-points")
public class AdminEmbedPointController {

    private final EmbedPointService embedPointService;
    private final InternalHttpClient internalHttpClient;

    /** 组织/员工数据在 EHR，这里只做代理（与消息配置同一键） */
    @Value("${approval.ehr-base-url:http://127.0.0.1:9006}")
    private String ehrBaseUrl;

    public AdminEmbedPointController(EmbedPointService embedPointService,
                                     InternalHttpClient internalHttpClient) {
        this.embedPointService = embedPointService;
        this.internalHttpClient = internalHttpClient;
    }

    @GetMapping
    @Permit(PermissionCodes.EMBED_VIEW)
    public R<List<EmbedPointVO>> list(@RequestParam(required = false) String keyword) {
        return R.ok(embedPointService.list(keyword));
    }

    @PostMapping
    @Permit(PermissionCodes.EMBED_MANAGE)
    public R<EmbedPointVO> create(@RequestBody EmbedPointCreate req) {
        return R.ok(embedPointService.create(req));
    }

    @PatchMapping("/{id}")
    @Permit(PermissionCodes.EMBED_MANAGE)
    public R<EmbedPointVO> update(@PathVariable Long id, @RequestBody EmbedPointPatch patch) {
        return R.ok(embedPointService.update(id, patch));
    }

    @GetMapping("/{id}/histories")
    @Permit(PermissionCodes.EMBED_VIEW)
    public R<List<EmbedPointHistoryVO>> histories(@PathVariable Long id) {
        return R.ok(embedPointService.histories(id));
    }

    /** 组织树（扁平，转自 EHR）——收件人「按部门」多选 */
    @GetMapping("/receivers/orgs")
    @Permit(PermissionCodes.EMBED_VIEW)
    public R<JsonNode> receiverOrgs() {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/tree"));
    }

    /** 已开户在职员工（转自 EHR）——收件人「按人」多选 */
    @GetMapping("/receivers/employees")
    @Permit(PermissionCodes.EMBED_VIEW)
    public R<JsonNode> receiverEmployees() {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/employees/active"));
    }
}
