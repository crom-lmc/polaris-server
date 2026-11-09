package com.laimengchao.sso.portal.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户统一待办：登录后聚合审批中心「待我审批」，按业务类型归类展示。
 * 审批数据由 settings 的 /internal/approval/pending 提供，portal 不落库。
 */
@Slf4j
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalTodoController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApprovalApi approvalApi;

    @GetMapping("/todos")
    public R<TodoSummary> todos() {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        List<ApprovalInstanceDTO> pending;
        try {
            var res = approvalApi.pending(user.getAccountId(), "pending");
            if (res == null || !res.ok()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        res == null ? "审批中心暂不可用" : res.getMessage());
            }
            pending = res.getData() == null ? List.of() : res.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("门户待办聚合失败 accountId={}", user.getAccountId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "审批中心暂不可用");
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        List<TodoItem> items = new ArrayList<>();
        for (ApprovalInstanceDTO d : pending) {
            String category = categoryOf(d.getBizType(), d.getBizName());
            counts.merge(category, 1, Integer::sum);
            TodoItem item = new TodoItem();
            item.setId(stringId(d.getId()));
            item.setTaskId(stringId(d.getMyTaskId()));
            item.setCategory(category);
            item.setBizType(d.getBizType());
            item.setBizName(d.getBizName());
            item.setTitle(d.getTitle());
            item.setApplicantName(d.getApplicantName());
            item.setOrgPath(d.getOrgPath());
            item.setCreatedAt(d.getCreatedAt() == null ? null : d.getCreatedAt().format(TIME_FMT));
            items.add(item);
        }

        TodoSummary summary = new TodoSummary();
        summary.setTotal(items.size());
        summary.setCategories(counts.entrySet().stream().map(e -> {
            Category c = new Category();
            c.setName(e.getKey());
            c.setCount(e.getValue());
            return c;
        }).toList());
        summary.setItems(items);
        return R.ok(summary);
    }

    private String categoryOf(String bizType, String bizName) {
        if (bizType == null) {
            return bizName == null ? "其他审批" : bizName;
        }
        return switch (bizType) {
            case "ehr.leave" -> "请假";
            case "ehr.overtime", "ehr.overtime.hr" -> "加班";
            case "ehr.regularize" -> "转正";
            case "ehr.recruit.offer", "ehr.recruit.offer.senior" -> "录用";
            default -> bizName == null || bizName.isBlank() ? "其他审批" : bizName;
        };
    }

    private String stringId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    @Data
    public static class TodoSummary {
        private int total;
        private List<Category> categories = new ArrayList<>();
        private List<TodoItem> items = new ArrayList<>();
    }

    @Data
    public static class Category {
        private String name;
        private int count;
    }

    @Data
    public static class TodoItem {
        private String id;
        private String taskId;
        private String category;
        private String bizType;
        private String bizName;
        private String title;
        private String applicantName;
        private String orgPath;
        private String createdAt;
    }
}
