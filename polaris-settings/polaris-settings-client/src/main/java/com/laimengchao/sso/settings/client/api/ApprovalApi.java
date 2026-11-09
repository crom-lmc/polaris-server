package com.laimengchao.sso.settings.client.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.settings.client.config.ApprovalFeignConfig;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalDelegateDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalPageDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 审批中心内部接口：业务服务发起审批、统一账号中心代为办理。
 * <p>
 * 走 {@code /internal/approval/**}（不经网关），凭 X-Internal-Token 准入。
 * <p>
 * 两边用法不一样，别混：
 * <ul>
 *   <li><b>业务服务（ehr）</b>只用 start / latest / detail——它只负责「发起」和「收结果」</li>
 *   <li><b>统一账号中心</b>用 pending / approve / transfer 等——它才是审批人操作的地方</li>
 * </ul>
 */
@FeignClient(
        name = "settings",
        contextId = "settings-approval",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = ApprovalFeignConfig.class)
public interface ApprovalApi {

    /** 发起审批；同一业务有在途单会返回业务错误 */
    @PostMapping("/internal/approval/start")
    R<ApprovalInstanceDTO> start(@RequestBody ApprovalStartDTO dto);

    /** 某笔业务的最新审批单（无则 data=null） */
    @GetMapping("/internal/approval/instances/latest")
    R<ApprovalInstanceDTO> latest(@RequestParam("bizType") String bizType,
                                  @RequestParam("bizId") String bizId);

    @GetMapping("/internal/approval/instances/{id}")
    R<ApprovalInstanceDTO> detail(@PathVariable("id") Long id,
                                  @RequestParam(value = "viewerId", required = false) Long viewerId);

    /**
     * 某业务主体是否有在途单。业务表不再存单值实例号，「能否再次发起」统一问审批中心，
     * 按 (subjectId, status=0) 判定；bizType 为空时跨场景查（同一员工同时只允许一张在途单）。
     */
    @GetMapping("/internal/approval/instances/running")
    R<Boolean> running(@RequestParam("subjectId") String subjectId,
                       @RequestParam(value = "bizType", required = false) String bizType);

    /** 待办五视图：pending 待我审批 / approved 已审批 / mine 我发起 / cc 抄送我 / delegated 我委托的 */
    @GetMapping("/internal/approval/pending")
    R<List<ApprovalInstanceDTO>> pending(@RequestParam("accountId") Long accountId,
                                         @RequestParam("scope") String scope);

    @PostMapping("/internal/approval/tasks/{taskId}/approve")
    R<ApprovalInstanceDTO> approve(@PathVariable("taskId") Long taskId,
                                   @RequestBody ApprovalActionDTO dto);

    @PostMapping("/internal/approval/tasks/{taskId}/transfer")
    R<Void> transfer(@PathVariable("taskId") Long taskId, @RequestBody ApprovalActionDTO dto);

    @PostMapping("/internal/approval/tasks/{taskId}/add-sign")
    R<Void> addSign(@PathVariable("taskId") Long taskId, @RequestBody ApprovalActionDTO dto);

    @PostMapping("/internal/approval/instances/{id}/withdraw")
    R<ApprovalInstanceDTO> withdraw(@PathVariable("id") Long id, @RequestBody ApprovalActionDTO dto);

    @PostMapping("/internal/approval/instances/{id}/terminate")
    R<ApprovalInstanceDTO> terminate(@PathVariable("id") Long id, @RequestBody ApprovalActionDTO dto);

    /** 登记审批委托：同一 remark 幂等更新；返回委托记录ID */
    @PostMapping("/internal/approval/delegates")
    R<Long> delegate(@RequestBody ApprovalDelegateDTO dto);

    /** 审批页面配置；未配置时 data=null，调用方走内置默认版式 */
    @GetMapping("/internal/approval/pages/{bizType}")
    R<ApprovalPageDTO> page(@PathVariable("bizType") String bizType,
                            @RequestParam(value = "view", required = false) String view);

    /* ---------------- 原始 JSON 透传 ----------------
     * 统一账号中心把审批中心当代理用，必须原样转发 JSON：
     * settings 侧所有雪花 ID 都靠 @JsonSerialize(ToStringSerializer) 转字符串，
     * 若在代理层再声明一套 VO，注解漏写就会丢精度。下面这些方法只服务账号中心，
     * 业务服务请继续用上面的强类型方法。
     */

    @GetMapping("/internal/approval/pending")
    R<JsonNode> pendingRaw(@RequestParam("accountId") Long accountId,
                           @RequestParam("scope") String scope);

    @GetMapping("/internal/approval/instances/{id}")
    R<JsonNode> detailRaw(@PathVariable("id") Long id,
                          @RequestParam(value = "viewerId", required = false) Long viewerId);

    @PostMapping("/internal/approval/instances/latest-batch")
    R<JsonNode> latestBatchRaw(@RequestBody Object refs);

    @GetMapping("/internal/approval/pages/{bizType}")
    R<JsonNode> pageRaw(@PathVariable("bizType") String bizType,
                        @RequestParam(value = "view", required = false) String view);

    @GetMapping("/internal/approval/instances/{id}/context")
    R<JsonNode> contextRaw(@PathVariable("id") Long id,
                           @RequestParam(value = "view", required = false) String view,
                           @RequestParam(value = "viewerId", required = false) Long viewerId);

    @GetMapping("/internal/approval/orgs/tree")
    R<JsonNode> orgTreeRaw();

    @GetMapping("/internal/approval/orgs/{id}/employees")
    R<JsonNode> orgEmployeesRaw(@PathVariable("id") Long orgId);

    @PostMapping("/internal/approval/tasks/{taskId}/approve")
    R<JsonNode> approveRaw(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/approval/tasks/{taskId}/transfer")
    R<JsonNode> transferRaw(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/approval/tasks/{taskId}/add-sign")
    R<JsonNode> addSignRaw(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/approval/instances/{id}/withdraw")
    R<JsonNode> withdrawRaw(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/approval/instances/{id}/terminate")
    R<JsonNode> terminateRaw(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    /* ---------------- 审批运营（时效 / 积压 / 催办 / 批量 / 导出） ---------------- */

    @GetMapping("/internal/approval/stats")
    R<JsonNode> statsRaw(@RequestParam(value = "appCode", required = false) String appCode,
                         @RequestParam(value = "bizType", required = false) String bizType,
                         @RequestParam(value = "orgId", required = false) Long orgId,
                         @RequestParam(value = "from", required = false) String from,
                         @RequestParam(value = "to", required = false) String to);

    @GetMapping("/internal/approval/stats/approver")
    R<JsonNode> approverStatsRaw(@RequestParam(value = "appCode", required = false) String appCode,
                                 @RequestParam(value = "bizType", required = false) String bizType,
                                 @RequestParam(value = "orgId", required = false) Long orgId,
                                 @RequestParam(value = "from", required = false) String from,
                                 @RequestParam(value = "to", required = false) String to);

    @GetMapping("/internal/approval/backlog")
    R<JsonNode> backlogRaw(@RequestParam(value = "appCode", required = false) String appCode,
                           @RequestParam(value = "bizType", required = false) String bizType,
                           @RequestParam(value = "orgId", required = false) Long orgId,
                           @RequestParam(value = "limit", required = false) Integer limit);

    @PostMapping("/internal/approval/instances/{id}/urge")
    R<JsonNode> urgeRaw(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/approval/tasks/batch")
    R<JsonNode> batchRaw(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/approval/export")
    R<JsonNode> exportRaw(@RequestParam("type") String type,
                          @RequestParam(value = "accountId", required = false) Long accountId,
                          @RequestParam(value = "appCode", required = false) String appCode,
                          @RequestParam(value = "bizType", required = false) String bizType,
                          @RequestParam(value = "orgId", required = false) Long orgId,
                          @RequestParam(value = "from", required = false) String from,
                          @RequestParam(value = "to", required = false) String to,
                          @RequestParam(value = "operatorId", required = false) Long operatorId);
}
