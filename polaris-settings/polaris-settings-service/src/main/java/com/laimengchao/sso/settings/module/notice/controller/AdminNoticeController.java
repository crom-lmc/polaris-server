package com.laimengchao.sso.settings.module.notice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.settings.module.approval.service.InternalHttpClient;
import com.laimengchao.sso.settings.module.notice.entity.NoticeRule;
import com.laimengchao.sso.settings.module.notice.service.NoticeService;
import com.laimengchao.sso.settings.module.notice.vo.NoticeRuleVO;
import com.laimengchao.sso.settings.security.Permit;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 管理端待办/消息配置（settings-web 使用）。
 * 查看 notice:view，增删改 notice:manage。
 */
@RestController
@RequestMapping("/api/settings/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;
    private final InternalHttpClient internalHttpClient;

    /** 组织/员工数据在 EHR，这里只做代理（与审批配置同一键） */
    @Value("${approval.ehr-base-url:http://127.0.0.1:9006}")
    private String ehrBaseUrl;

    public AdminNoticeController(NoticeService noticeService, InternalHttpClient internalHttpClient) {
        this.noticeService = noticeService;
        this.internalHttpClient = internalHttpClient;
    }

    /** 组织树（扁平，转自 EHR）——规则「指定接收部门」多选用 */
    @GetMapping("/receivers/orgs")
    @Permit(PermissionCodes.NOTICE_VIEW)
    public R<JsonNode> receiverOrgs() {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/tree"));
    }

    /** 已开户在职员工（转自 EHR）——规则「指定接收人」多选用 */
    @GetMapping("/receivers/employees")
    @Permit(PermissionCodes.NOTICE_VIEW)
    public R<JsonNode> receiverEmployees() {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/employees/active"));
    }

    @GetMapping("/rules")
    @Permit(PermissionCodes.NOTICE_VIEW)
    public R<List<NoticeRuleVO>> rules(@RequestParam(required = false) String keyword) {
        return R.ok(noticeService.listRules(keyword));
    }

    @PostMapping("/rules")
    @Permit(PermissionCodes.NOTICE_MANAGE)
    public R<NoticeRuleVO> create(@RequestBody RuleRequest body) {
        return R.ok(noticeService.createRule(body.toEntity()));
    }

    @PatchMapping("/rules/{id}")
    @Permit(PermissionCodes.NOTICE_MANAGE)
    public R<NoticeRuleVO> update(@PathVariable Long id, @RequestBody RuleRequest body) {
        return R.ok(noticeService.updateRule(id, body.toEntity()));
    }

    @DeleteMapping("/rules/{id}")
    @Permit(PermissionCodes.NOTICE_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        noticeService.deleteRule(id);
        return R.ok();
    }

    @Data
    public static class RuleRequest {
        private String code;
        /** 归属应用编码，空=通用 */
        private String appCode;
        private String module;
        private String name;
        private String titleTpl;
        private String contentTpl;
        /** 1 待办 / 2 消息 */
        private Integer channel;
        private Integer email;
        private Integer enabled;
        private Integer sort;
        /** 指定接收账号 ID（逗号分隔），空=由业务方调用时决定 */
        private String receiverAccountIds;
        /** 指定接收部门（业务侧组织节点 ID，逗号分隔，含下级） */
        private String receiverOrgIds;

        NoticeRule toEntity() {
            NoticeRule rule = new NoticeRule();
            rule.setCode(code);
            rule.setAppCode(appCode);
            rule.setModule(module);
            rule.setName(name);
            rule.setTitleTpl(titleTpl);
            rule.setContentTpl(contentTpl);
            rule.setChannel(channel);
            rule.setEmail(email);
            rule.setEnabled(enabled);
            rule.setSort(sort);
            rule.setReceiverAccountIds(receiverAccountIds);
            rule.setReceiverOrgIds(receiverOrgIds);
            return rule;
        }
    }
}
