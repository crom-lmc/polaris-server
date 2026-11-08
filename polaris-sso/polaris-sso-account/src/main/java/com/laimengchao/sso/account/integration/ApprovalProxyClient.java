package com.laimengchao.sso.account.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.account.dto.request.ApprovalActionRequest;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 审批中心（settings）的内部接口客户端（Feign）。
 * <p>
 * <b>为什么由账号中心代理</b>：审批模板和审批实例都在 settings 库，
 * 但 settings 的 {@code /api/settings/**} 只认 settings 作用域的令牌，
 * 前端拿着 sso 令牌去打会被应用边界拦掉（这是对的，不能为一个功能给 settings 开后门）。
 * 而审批中心的「我是谁」必须来自统一账号中心——所以让账号中心代收请求、
 * 补上操作人身份、再用内部令牌转给 settings。
 * <p>
 * <b>为什么返回原始 JsonNode 而不是复制一套 VO</b>：settings 侧的 VO 里
 * 所有雪花 ID 都靠 {@code @JsonSerialize(ToStringSerializer)} 转成字符串，
 * 复制一份到本模块，注解一旦漏写就会出现「19 位 ID 在浏览器里被抹掉后几位」的诡异 bug。
 * 报文原样透传，这些约定就只在一个地方维护。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalProxyClient {

    private final ApprovalApi approvalApi;

    /* ---------------- 查询 ---------------- */

    /** 待办五视图：pending 待我审批 / approved 已审批 / mine 我发起 / cc 抄送我 / delegated 我委托出去的 */
    public JsonNode list(Long accountId, String scope) {
        return call(() -> approvalApi.pendingRaw(accountId, scope), "/internal/approval/pending");
    }

    /** 审批单详情；viewerId 用来回填「我的待办任务」 */
    public JsonNode detail(Long instanceId, Long viewerId) {
        return call(() -> approvalApi.detailRaw(instanceId, viewerId),
                "/internal/approval/instances/" + instanceId);
    }

    /**
     * 批量反查「某笔业务的最新审批单」：业务列表显示「XX审批被驳回」用。
     * 只回精简信息，详情仍走 {@link #detail(Long, Long)}。
     */
    public JsonNode latestBatch(Object refs) {
        return call(() -> approvalApi.latestBatchRaw(refs == null ? List.of() : refs),
                "/internal/approval/instances/latest-batch");
    }

    /** 审批页面配置（审批弹窗的信息区版式）；未配置返回 null，前端走内置默认 */
    public JsonNode page(String bizType, String view) {
        return call(() -> approvalApi.pageRaw(bizType, view == null ? "APPROVAL" : view),
                "/internal/approval/pages/" + bizType);
    }

    /**
     * 审批弹窗的渲染上下文：版式 + 已算好的字段值。
     * 前端拿到直接铺开，不再自己拼「所属组织/职位/入职日期」那些行。
     */
    public JsonNode context(Long instanceId, Long viewerId, String view) {
        return call(() -> approvalApi.contextRaw(instanceId, view == null ? "APPROVAL" : view, viewerId),
                "/internal/approval/instances/" + instanceId + "/context");
    }

    /* ---------------- 动作 ---------------- */

    /** 全量组织树（扁平，前端组树）：转办 / 加签按组织选人用，转自 EHR */
    public JsonNode orgTree() {
        return call(approvalApi::orgTreeRaw, "/internal/approval/orgs/tree");
    }

    /** 某组织直属成员（已开户、未离职）：转办 / 加签候选人 */
    public JsonNode orgEmployees(Long orgId) {
        return call(() -> approvalApi.orgEmployeesRaw(orgId),
                "/internal/approval/orgs/" + orgId + "/employees");
    }

    public JsonNode approve(Long taskId, Long operatorId, String operatorName, ApprovalActionRequest req) {
        Map<String, Object> body = actionBody(taskId, operatorId, operatorName, req);
        body.put("pass", req.getPass() == null || req.getPass());
        return call(() -> approvalApi.approveRaw(taskId, body), "/internal/approval/tasks/" + taskId + "/approve");
    }

    public void transfer(Long taskId, Long operatorId, String operatorName, ApprovalActionRequest req) {
        requireTarget(req, "转办必须指定接手人");
        run(() -> approvalApi.transferRaw(taskId, actionBody(taskId, operatorId, operatorName, req)),
                "/internal/approval/tasks/" + taskId + "/transfer");
    }

    public void addSign(Long taskId, Long operatorId, String operatorName, ApprovalActionRequest req) {
        requireTarget(req, "加签必须指定被加签人");
        run(() -> approvalApi.addSignRaw(taskId, actionBody(taskId, operatorId, operatorName, req)),
                "/internal/approval/tasks/" + taskId + "/add-sign");
    }

    public JsonNode withdraw(Long instanceId, Long operatorId, String operatorName, ApprovalActionRequest req) {
        Map<String, Object> body = actionBody(null, operatorId, operatorName, req);
        return call(() -> approvalApi.withdrawRaw(instanceId, body),
                "/internal/approval/instances/" + instanceId + "/withdraw");
    }

    public JsonNode terminate(Long instanceId, Long operatorId, String operatorName, ApprovalActionRequest req) {
        Map<String, Object> body = actionBody(null, operatorId, operatorName, req);
        return call(() -> approvalApi.terminateRaw(instanceId, body),
                "/internal/approval/instances/" + instanceId + "/terminate");
    }

    /* ---------------- 内部实现 ---------------- */

    private Map<String, Object> actionBody(Long taskId, Long operatorId, String operatorName,
                                          ApprovalActionRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operatorId", operatorId);
        m.put("operatorName", operatorName);
        if (req != null) {
            m.put("pass", req.getPass());
            m.put("note", req.getNote());
            m.put("position", req.getPosition());
            // 雪花 ID 用字符串发：settings 侧是 Long，Jackson 会自动转
            if (req.getTargetId() != null) {
                m.put("targetId", String.valueOf(req.getTargetId()));
            }
        }
        return m;
    }

    private void requireTarget(ApprovalActionRequest req, String message) {
        if (req == null || req.getTargetId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 发起请求并取 R 壳里的 data。
     * <p>
     * settings 的业务错误（如 8205 审批单已结束）原样抛出它的 code 与 message：
     * 前端拿到的提示要和审批中心完全一致，代理层不该改写业务语义。
     */
    private JsonNode call(Supplier<R<JsonNode>> supplier, String path) {
        try {
            R<JsonNode> res = supplier.get();
            if (res == null) {
                return null;
            }
            if (!res.ok()) {
                String msg = res.getMessage() == null ? "审批中心返回错误" : res.getMessage();
                log.warn("审批中心返回业务错误：path={} code={} msg={}", path, res.getCode(), msg);
                throw new BizException(res.getCode(), msg);
            }
            return res.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用审批中心失败：path={} err={}", path, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "审批中心暂时不可用，请稍后重试");
        }
    }

    private void run(Runnable action, String path) {
        try {
            action.run();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用审批中心失败：path={} err={}", path, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "审批中心暂时不可用，请稍后重试");
        }
    }
}
