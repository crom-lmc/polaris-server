package com.laimengchao.sso.ehr.module.approval.support;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 审批发起器：把「本地业务写入 → 远程发起 → 回写实例号」拆成三段，根治回调竞态。
 *
 * <p><b>为什么必须三段式</b>：settings 的 {@code start()} 在模板没有审批人、条件全假或
 * 步骤被跳过时，会在同一次调用里把实例直接推到终态，并在事务提交后
 * <b>同步</b>回调业务方（{@code ApprovalCallbackService.afterCommit → dispatch} 是同步 HTTP）。
 * 如果业务方把本地 insert/update 和远程 start 塞进同一个 {@code @Transactional}，
 * 回调线程读到的是<b>尚未提交</b>的业务行，按「业务行不存在」正常返回，
 * 审批单标记回调成功、业务表却永远卡在「待审批」，还挂着一张已终结的实例号。
 *
 * <p>调用方约定（三段各管各的事务，缺一不可）：
 * <ol>
 *   <li>先用 {@link #inTx(Runnable)} 把本地业务行写入并<b>提交</b>；</li>
 *   <li>调 {@link #launch(Supplier, LongConsumer, Runnable)} 远程发起；</li>
 *   <li>launch 内部只在实例仍是 RUNNING 时才回写实例号；若实例发起即终态，
 *       说明回调已经把业务行写成终态，<b>绝不能</b>再把实例号挂回去。</li>
 * </ol>
 *
 * <p>另外处理「start 超时但 settings 其实已建单」：失败时查一次 latest，
 * 有在途实例就补绑，确认没有才执行业务侧补偿，避免留下「有单没审批」或
 * 「本地撤销了、审批还在跑」的孤儿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalLauncher {

    /** 0 = 进行中；只有 RUNNING 才有「在途」语义，其余都是终态 */
    private static final int STATUS_RUNNING = 0;

    private final ApprovalApi approvalApi;
    private final PlatformTransactionManager transactionManager;

    /**
     * 远程发起审批，并按需条件回写实例号。
     *
     * @param startSupplier 纯构造发起请求，<b>不得包含远程调用</b>
     * @param bindInstance  条件回写实例号（在独立事务里执行，务必带 isNull / 在途状态条件）
     * @param onOrphan      确认发起失败后的本地补偿（在独立事务里执行）
     * @return 审批中心的实例 DTO；status 可能已是终态
     */
    public ApprovalInstanceDTO launch(Supplier<ApprovalStartDTO> startSupplier,
                                      LongConsumer bindInstance,
                                      Runnable onOrphan) {
        ApprovalStartDTO dto = startSupplier.get();
        fillSubject(dto);
        try {
            R<ApprovalInstanceDTO> resp = approvalApi.start(dto);
            if (resp == null || !resp.ok() || resp.getData() == null || resp.getData().getId() == null) {
                throw new BizException(EhrErrorCode.APPROVAL_START_FAILED, messageOf(resp));
            }
            ApprovalInstanceDTO inst = resp.getData();
            if (!Integer.valueOf(STATUS_RUNNING).equals(inst.getStatus())) {
                log.info("审批实例发起即终态，跳过实例号回写：bizType={} bizId={} instance={} status={}",
                        dto.getBizType(), dto.getBizId(), inst.getId(), inst.getStatus());
                return inst;
            }
            // 实例号已退役：业务表不再保存 approval_instance_id 等单值指针，
            // 在途判断统一按 (bizType, subjectId) 查审批中心 running 接口。
            // bindInstance 保留入参只为兼容既有调用签名，不再执行。
            return inst;
        } catch (BizException e) {
            ApprovalInstanceDTO recovered = recover(dto, bindInstance, onOrphan);
            if (recovered != null) {
                return recovered;
            }
            throw e;
        } catch (Exception e) {
            log.error("发起审批失败 bizType={} bizId={}", dto.getBizType(), dto.getBizId(), e);
            ApprovalInstanceDTO recovered = recover(dto, bindInstance, onOrphan);
            if (recovered != null) {
                return recovered;
            }
            throw new BizException(EhrErrorCode.APPROVAL_START_FAILED,
                    "发起审批失败（审批中心不可用），本次提交已回滚，请稍后重试");
        }
    }

    /** 在独立事务里执行一段本地写入；用于把远程调用前后的本地操作各自提交 */
    public void inTx(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    /**
     * 发起前显式声明「业务主体 ID」与 bizId 语义，业务方不必逐个改 buildStart。
     * 主体键按快照里的 employeeId → orgId 顺序取（负责人变更只有 orgId），
     * 都取不到就留给审批中心按场景 subject_field / bizId 兜底。
     */
    private void fillSubject(ApprovalStartDTO dto) {
        if (isBlank(dto.getSubjectId())) {
            java.util.Map<String, Object> form = dto.getFormData();
            Object v = form == null ? null : form.get("employeeId");
            if (v == null && form != null) {
                v = form.get("orgId");
            }
            if (v != null) {
                dto.setSubjectId(String.valueOf(v));
            }
        }
        if (isBlank(dto.getBizIdKind())) {
            String sid = dto.getSubjectId();
            dto.setBizIdKind(sid != null && sid.equals(dto.getBizId()) ? "SUBJECT" : "DOC");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 业务主体是否有在途审批单。业务表上的单值实例号已退役，
     * 「能否再次发起」统一问审批中心（按 appr_instance.subject_id + status=0）。
     * 审批中心不可用时 fail-closed，避免绕过在途限制重复发起。
     */
    public boolean hasRunning(String subjectId, String bizType) {
        if (isBlank(subjectId)) {
            return false;
        }
        try {
            R<Boolean> resp = approvalApi.running(subjectId, bizType);
            return resp != null && resp.ok() && Boolean.TRUE.equals(resp.getData());
        } catch (Exception e) {
            log.warn("查询在途审批单失败 subjectId={} bizType={}：{}", subjectId, bizType, e.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_START_FAILED, "审批中心不可用，请稍后重试");
        }
    }

    /**
     * 撤销业务申请时用：按 (bizType, bizId) 找回当前在途实例 ID。
     * 业务行不再存实例号，但 settings 的 latest 仍按 bizId 精确定位本次单据。
     */
    public Long latestRunningId(String bizType, String bizId) {
        try {
            R<ApprovalInstanceDTO> r = approvalApi.latest(bizType, bizId);
            if (r != null && r.ok() && r.getData() != null && r.getData().getId() != null
                    && Integer.valueOf(STATUS_RUNNING).equals(r.getData().getStatus())) {
                return r.getData().getId();
            }
        } catch (Exception e) {
            log.warn("查询在途审批单失败 bizType={} bizId={}：{}", bizType, bizId, e.getMessage());
        }
        return null;
    }

    /**
     * start 抛异常 / 被拒时，先查 latest 兜底：settings 可能已经建单只是响应丢了。
     * 确实没有在途实例，才让业务侧补偿（撤销刚落库的单 / 还原状态）。
     */
    private ApprovalInstanceDTO recover(ApprovalStartDTO dto, LongConsumer bindInstance, Runnable onOrphan) {
        try {
            R<ApprovalInstanceDTO> latest = approvalApi.latest(dto.getBizType(), dto.getBizId());
            if (latest != null && latest.ok() && latest.getData() != null
                    && latest.getData().getId() != null
                    && Integer.valueOf(STATUS_RUNNING).equals(latest.getData().getStatus())) {
                ApprovalInstanceDTO inst = latest.getData();
                log.warn("发起审批响应异常但审批单已创建（无需补绑实例号）：bizType={} bizId={} instance={}",
                        dto.getBizType(), dto.getBizId(), inst.getId());
                return inst;
            }
        } catch (Exception e) {
            log.warn("发起失败后查询 latest 也失败，走本地补偿：{}", e.getMessage());
        }
        inTx(onOrphan);
        return null;
    }

    private static String messageOf(R<?> resp) {
        String msg = resp == null || resp.getMessage() == null ? "" : resp.getMessage();
        return msg.isBlank() ? "发起审批失败，请稍后重试" : msg;
    }
}
